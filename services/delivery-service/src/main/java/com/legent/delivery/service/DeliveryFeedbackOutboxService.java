package com.legent.delivery.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.delivery.domain.DeliveryFeedbackOutboxEvent;
import com.legent.delivery.event.DeliveryEventPublisher;
import com.legent.delivery.event.DeliveryFeedbackMessage;
import com.legent.delivery.repository.DeliveryFeedbackOutboxEventRepository;
import com.legent.kafka.model.EventEnvelope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryFeedbackOutboxService {

    private static final Duration PUBLISHING_LEASE = Duration.ofMinutes(5);
    private static final TypeReference<Map<String, String>> PAYLOAD_TYPE = new TypeReference<>() {};

    private final DeliveryFeedbackOutboxEventRepository outboxRepository;
    private final DeliveryEventPublisher deliveryEventPublisher;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${legent.delivery.feedback-outbox.max-attempts:8}")
    private int maxAttempts;

    @PostConstruct
    void registerBacklogMetrics() {
        Gauge.builder("legent.outbox.ready.depth", this, DeliveryFeedbackOutboxService::readyDepth)
                .description("Ready outbox events waiting to be published")
                .tag("queue", "delivery_feedback")
                .register(meterRegistry);
        Gauge.builder("legent.outbox.oldest.ready.age.seconds", this, DeliveryFeedbackOutboxService::oldestReadyAgeSeconds)
                .description("Age in seconds of the oldest ready outbox event")
                .tag("queue", "delivery_feedback")
                .register(meterRegistry);
    }

    @Transactional
    public void enqueueEmailSent(String tenantId, String workspaceId, String messageId, String campaignId, String jobId, String batchId, String subscriberId, Map<String, String> metadata) {
        enqueue(deliveryEventPublisher.emailSentMessage(tenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, metadata));
    }

    @Transactional
    public void enqueueEmailFailed(String tenantId, String workspaceId, String messageId, String campaignId, String jobId, String batchId, String subscriberId, String reason, Map<String, String> metadata) {
        enqueue(deliveryEventPublisher.emailFailedMessage(tenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, reason, metadata));
    }

    @Transactional
    public void enqueueRetryScheduled(String tenantId, String workspaceId, String messageId, long attemptCount, String nextRetryAt, Map<String, String> metadata) {
        enqueue(deliveryEventPublisher.retryScheduledMessage(tenantId, workspaceId, messageId, attemptCount, nextRetryAt, metadata));
    }

    @Transactional
    public void enqueueEmailBounced(String tenantId, String workspaceId, String email, String reason, String senderDomain, Map<String, String> metadata) {
        enqueue(deliveryEventPublisher.emailBouncedMessage(tenantId, workspaceId, email, reason, senderDomain, metadata));
    }

    @Transactional
    public void enqueue(DeliveryFeedbackMessage message) {
        if (message == null || message.envelope() == null) {
            throw new IllegalArgumentException("delivery feedback message is required");
        }
        EventEnvelope<Map<String, String>> envelope = message.envelope();
        if (outboxRepository.existsByTenantIdAndWorkspaceIdAndEventTypeAndTransitionKeyAndDeletedAtIsNull(
                envelope.getTenantId(),
                envelope.getWorkspaceId(),
                envelope.getEventType(),
                message.transitionKey())) {
            return;
        }

        DeliveryFeedbackOutboxEvent event = toEntity(message);
        outboxRepository.save(event);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishPending(event.getId(), event.getTenantId(), event.getWorkspaceId());
                }
            });
        } else {
            publishPending(event.getId(), event.getTenantId(), event.getWorkspaceId());
        }
    }

    @Scheduled(fixedDelayString = "${legent.delivery.feedback-outbox.poll-ms:10000}")
    public void publishReadyEvents() {
        List<DeliveryFeedbackOutboxEvent> ready = outboxRepository.findTop100ByStatusInAndNextAttemptAtLessThanEqualAndDeletedAtIsNullOrderByCreatedAtAsc(
                List.of(DeliveryFeedbackOutboxEvent.STATUS_PENDING, DeliveryFeedbackOutboxEvent.STATUS_PUBLISHING),
                Instant.now());
        ready.forEach(event -> publishPending(event.getId(), event.getTenantId(), event.getWorkspaceId()));
    }

    public void publishPending(String outboxId, String tenantId, String workspaceId) {
        if (isBlank(outboxId) || isBlank(tenantId) || isBlank(workspaceId)) {
            return;
        }
        Instant now = Instant.now();
        int claimed = outboxRepository.claimReadyForPublish(
                outboxId,
                tenantId,
                workspaceId,
                List.of(DeliveryFeedbackOutboxEvent.STATUS_PENDING, DeliveryFeedbackOutboxEvent.STATUS_PUBLISHING),
                now,
                DeliveryFeedbackOutboxEvent.STATUS_PUBLISHING,
                now.plus(PUBLISHING_LEASE));
        if (claimed == 0) {
            return;
        }

        DeliveryFeedbackOutboxEvent event = outboxRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(outboxId, tenantId, workspaceId).orElse(null);
        if (event == null) {
            return;
        }

        long publishStartedNanos = System.nanoTime();
        try {
            deliveryEventPublisher.publishOrThrow(toMessage(event));
            recordPublishDuration(publishStartedNanos, event, "published");
            event.setStatus(DeliveryFeedbackOutboxEvent.STATUS_PUBLISHED);
            event.setPublishedAt(Instant.now());
            event.setLastError(null);
            outboxRepository.save(event);
        } catch (Exception ex) {
            recordPublishDuration(publishStartedNanos, event, "failed");
            int attempts = event.getAttempts();
            boolean exhausted = attempts >= event.getMaxAttempts();
            event.setStatus(exhausted ? DeliveryFeedbackOutboxEvent.STATUS_FAILED : DeliveryFeedbackOutboxEvent.STATUS_PENDING);
            event.setLastError(trimError(ex));
            event.setNextAttemptAt(Instant.now().plus(retryDelay(attempts)));
            outboxRepository.save(event);
            if (exhausted) {
                Counter.builder("legent.delivery.feedback.outbox.exhausted")
                        .description("Delivery feedback outbox events that exhausted publish attempts")
                        .tag("eventType", event.getEventType())
                        .register(meterRegistry)
                        .increment();
                log.error("Delivery feedback outbox event {} failed after {} attempt(s)", outboxId, attempts, ex);
            } else {
                log.warn("Delivery feedback outbox event {} publish failed, retry scheduled at {}",
                        outboxId, event.getNextAttemptAt(), ex);
            }
        }
    }

    private DeliveryFeedbackOutboxEvent toEntity(DeliveryFeedbackMessage message) {
        EventEnvelope<Map<String, String>> envelope = message.envelope();
        DeliveryFeedbackOutboxEvent event = new DeliveryFeedbackOutboxEvent();
        event.setTenantId(envelope.getTenantId());
        event.setWorkspaceId(envelope.getWorkspaceId());
        event.setEnvironmentId(envelope.getEnvironmentId());
        event.setActorId(envelope.getActorId());
        event.setOwnershipScope(envelope.getOwnershipScope() == null ? "WORKSPACE" : envelope.getOwnershipScope());
        event.setTopic(message.topic());
        event.setEventType(envelope.getEventType());
        event.setEventId(envelope.getEventId());
        event.setEventTimestamp(envelope.getTimestamp());
        event.setSource(envelope.getSource());
        event.setSchemaVersion(envelope.getSchemaVersion());
        event.setRetryCount(envelope.getRetryCount());
        event.setCorrelationId(envelope.getCorrelationId());
        event.setIdempotencyKey(envelope.getIdempotencyKey());
        event.setMessageId(message.messageId());
        event.setCampaignId(message.campaignId());
        event.setJobId(message.jobId());
        event.setBatchId(message.batchId());
        event.setSubscriberId(message.subscriberId());
        event.setRecipientEmail(message.recipientEmail());
        event.setSenderDomain(message.senderDomain());
        event.setTransitionKey(message.transitionKey());
        event.setPartitionKey(message.partitionKey());
        event.setPayloadJson(writePayload(envelope.getPayload(), envelope.getEventId()));
        event.setMaxAttempts(maxAttempts);
        event.setNextAttemptAt(Instant.now());
        return event;
    }

    private DeliveryFeedbackMessage toMessage(DeliveryFeedbackOutboxEvent event) {
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.<Map<String, String>>builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .timestamp(event.getEventTimestamp())
                .tenantId(event.getTenantId())
                .workspaceId(event.getWorkspaceId())
                .environmentId(event.getEnvironmentId())
                .actorId(event.getActorId())
                .ownershipScope(event.getOwnershipScope())
                .correlationId(event.getCorrelationId())
                .source(event.getSource())
                .schemaVersion(event.getSchemaVersion())
                .idempotencyKey(event.getIdempotencyKey())
                .retryCount(event.getRetryCount())
                .payload(readPayload(event))
                .build();
        return new DeliveryFeedbackMessage(
                event.getTopic(),
                event.getTransitionKey(),
                event.getPartitionKey(),
                event.getMessageId(),
                event.getCampaignId(),
                event.getJobId(),
                event.getBatchId(),
                event.getSubscriberId(),
                event.getRecipientEmail(),
                event.getSenderDomain(),
                envelope);
    }

    private String writePayload(Map<String, String> payload, String eventId) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize delivery feedback outbox payload " + eventId, ex);
        }
    }

    private Map<String, String> readPayload(DeliveryFeedbackOutboxEvent event) {
        try {
            return objectMapper.readValue(event.getPayloadJson(), PAYLOAD_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize delivery feedback outbox payload " + event.getId(), ex);
        }
    }

    private Duration retryDelay(int attempts) {
        long seconds = Math.min(300, Math.max(1, 1L << Math.min(attempts, 8)));
        return Duration.ofSeconds(seconds);
    }

    private double readyDepth() {
        return outboxRepository.countByStatusInAndNextAttemptAtLessThanEqualAndDeletedAtIsNull(readyStatuses(), Instant.now());
    }

    private double oldestReadyAgeSeconds() {
        Instant now = Instant.now();
        return outboxRepository.findOldestReadyCreatedAt(readyStatuses(), now)
                .map(oldest -> Math.max(0, Duration.between(oldest, now).toSeconds()))
                .orElse(0L);
    }

    private List<String> readyStatuses() {
        return List.of(DeliveryFeedbackOutboxEvent.STATUS_PENDING, DeliveryFeedbackOutboxEvent.STATUS_PUBLISHING);
    }

    private String trimError(Exception ex) {
        Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void recordPublishDuration(long startedNanos, DeliveryFeedbackOutboxEvent event, String outcome) {
        Duration duration = Duration.ofNanos(System.nanoTime() - startedNanos);
        Timer.builder("legent.delivery.feedback.outbox.publish.duration")
                .description("Time spent publishing a claimed delivery feedback outbox event to Kafka")
                .tag("eventType", event.getEventType())
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(duration);
        DistributionSummary.builder("legent.delivery.feedback.outbox.publish.lease.utilization")
                .description("Ratio of publish duration to delivery feedback outbox publishing lease")
                .tag("eventType", event.getEventType())
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record((double) duration.toNanos() / PUBLISHING_LEASE.toNanos());
    }
}

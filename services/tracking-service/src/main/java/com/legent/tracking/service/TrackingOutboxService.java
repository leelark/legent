package com.legent.tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.tracking.domain.TrackingOutboxEvent;
import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.event.TrackingEventPublisher;
import com.legent.tracking.repository.TrackingOutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingOutboxService {

    private static final Duration PUBLISHING_LEASE = Duration.ofMinutes(5);

    private final TrackingOutboxEventRepository outboxRepository;
    private final TrackingEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${legent.tracking.outbox.max-attempts:8}")
    private int maxAttempts;

    @Value("${legent.tracking.outbox.publish-after-commit:false}")
    private boolean publishAfterCommit;

    @Value("${legent.tracking.outbox.poll-batch-size:100}")
    private int pollBatchSize;

    @Value("${legent.tracking.outbox.poll-max-pages:1}")
    private int pollMaxPages;

    @PostConstruct
    void registerBacklogMetrics() {
        Gauge.builder("legent.outbox.ready.depth", this, TrackingOutboxService::readyDepth)
                .description("Ready outbox events waiting to be published")
                .tag("queue", "tracking")
                .register(meterRegistry);
        Gauge.builder("legent.outbox.oldest.ready.age.seconds", this, TrackingOutboxService::oldestReadyAgeSeconds)
                .description("Age in seconds of the oldest ready outbox event")
                .tag("queue", "tracking")
                .register(meterRegistry);
    }

    @Transactional
    public void enqueue(TrackingDto.RawEventPayload payload) {
        TrackingOutboxEvent event = new TrackingOutboxEvent();
        event.setId(payload.getId());
        event.setTenantId(payload.getTenantId());
        event.setWorkspaceId(payload.getWorkspaceId());
        event.setEventType(payload.getEventType());
        event.setMessageId(payload.getMessageId());
        event.setIdempotencyKey(payload.getIdempotencyKey());
        event.setPayloadJson(writePayload(payload));
        outboxRepository.save(event);

        if (!publishAfterCommit) {
            return;
        }
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

    @Scheduled(fixedDelayString = "${legent.tracking.outbox.poll-ms:10000}")
    public void publishReadyEvents() {
        int batchSize = bounded(pollBatchSize, 1, 1000);
        int maxPages = bounded(pollMaxPages, 1, 20);
        for (int page = 0; page < maxPages; page++) {
            List<TrackingOutboxEvent> ready = outboxRepository.findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                    readyStatuses(),
                    Instant.now(),
                    PageRequest.of(0, batchSize));
            if (ready.isEmpty()) {
                return;
            }
            ready.forEach(event -> publishPending(event.getId(), event.getTenantId(), event.getWorkspaceId()));
            if (ready.size() < batchSize) {
                return;
            }
        }
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
                List.of(TrackingOutboxEvent.STATUS_PENDING, TrackingOutboxEvent.STATUS_PUBLISHING),
                now,
                TrackingOutboxEvent.STATUS_PUBLISHING,
                now.plus(PUBLISHING_LEASE));
        if (claimed == 0) {
            return;
        }

        TrackingOutboxEvent event = outboxRepository.findByIdAndTenantIdAndWorkspaceId(outboxId, tenantId, workspaceId).orElse(null);
        if (event == null) {
            return;
        }

        long publishStartedNanos = System.nanoTime();
        try {
            TrackingDto.RawEventPayload payload = objectMapper.readValue(event.getPayloadJson(), TrackingDto.RawEventPayload.class);
            eventPublisher.publishIngestedEventOrThrow(payload);
            recordPublishDuration(publishStartedNanos, event, "published");
            event.setStatus(TrackingOutboxEvent.STATUS_PUBLISHED);
            event.setPublishedAt(Instant.now());
            event.setLastError(null);
            outboxRepository.save(event);
        } catch (Exception ex) {
            recordPublishDuration(publishStartedNanos, event, "failed");
            int attempts = event.getAttempts();
            boolean exhausted = attempts >= maxAttempts;
            event.setStatus(exhausted ? TrackingOutboxEvent.STATUS_FAILED : TrackingOutboxEvent.STATUS_PENDING);
            event.setLastError(trimError(ex));
            event.setNextAttemptAt(Instant.now().plus(retryDelay(attempts)));
            outboxRepository.save(event);
            if (exhausted) {
                log.error("Tracking outbox event {} failed after {} attempt(s)", outboxId, attempts, ex);
            } else {
                log.warn("Tracking outbox event {} publish failed, retry scheduled at {}", outboxId, event.getNextAttemptAt(), ex);
            }
        }
    }

    private String writePayload(TrackingDto.RawEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize tracking outbox payload " + payload.getId(), ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Duration retryDelay(int attempts) {
        long seconds = Math.min(300, Math.max(1, 1L << Math.min(attempts, 8)));
        return Duration.ofSeconds(seconds);
    }

    private double readyDepth() {
        return outboxRepository.countByStatusInAndNextAttemptAtLessThanEqual(readyStatuses(), Instant.now());
    }

    private double oldestReadyAgeSeconds() {
        Instant now = Instant.now();
        return outboxRepository.findOldestReadyCreatedAt(readyStatuses(), now)
                .map(oldest -> Math.max(0, Duration.between(oldest, now).toSeconds()))
                .orElse(0L);
    }

    private List<String> readyStatuses() {
        return List.of(TrackingOutboxEvent.STATUS_PENDING, TrackingOutboxEvent.STATUS_PUBLISHING);
    }

    private int bounded(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private String trimError(Exception ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private void recordPublishDuration(long startedNanos, TrackingOutboxEvent event, String outcome) {
        Duration duration = Duration.ofNanos(System.nanoTime() - startedNanos);
        Timer.builder("legent.tracking.outbox.publish.duration")
                .description("Time spent publishing a claimed tracking outbox event to Kafka")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(duration);
        DistributionSummary.builder("legent.tracking.outbox.publish.lease.utilization")
                .description("Ratio of publish duration to tracking outbox publishing lease")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record((double) duration.toNanos() / PUBLISHING_LEASE.toNanos());
        if (!duration.minus(PUBLISHING_LEASE).isNegative()) {
            Counter.builder("legent.tracking.outbox.publish.lease.exceeded")
                    .description("Tracking outbox publishes that exceeded the claim lease")
                    .tag("eventType", event.getEventType() == null ? "UNKNOWN" : event.getEventType())
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .increment();
        }
    }
}

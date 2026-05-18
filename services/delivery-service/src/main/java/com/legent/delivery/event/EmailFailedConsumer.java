package com.legent.delivery.event;

import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.delivery.service.DeliveryEventIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "legent.delivery.failed-consumer.enabled", havingValue = "true", matchIfMissing = false)
public class EmailFailedConsumer {

    private final EventPublisher eventPublisher;
    private final DeliveryEventIdempotencyService idempotencyService;

    @KafkaListener(topics = AppConstants.TOPIC_EMAIL_FAILED, groupId = AppConstants.GROUP_DELIVERY_FAILED, concurrency = "3")
    public void consumeEmailFailed(EventEnvelope<Map<String, Object>> envelope) {
        String tenantId = envelope != null ? envelope.getTenantId() : null;
        String workspaceId = null;
        String eventId = envelope != null ? envelope.getEventId() : null;
        String idempotencyKey = envelope != null ? envelope.getIdempotencyKey() : null;
        boolean claimed = false;
        try {
            if (envelope == null) {
                throw new InvalidFailedEmailEventException("Missing failed-email event envelope");
            }
            tenantId = requireTenantId(envelope);
            eventId = normalize(envelope.getEventId());
            idempotencyKey = normalize(envelope.getIdempotencyKey());
            requireEventType(envelope);
            if (!hasClaimKey(eventId, idempotencyKey)) {
                throw new InvalidFailedEmailEventException("Missing eventId or idempotencyKey for failed-email event");
            }
            Map<String, Object> payload = envelope.getPayload() != null ? envelope.getPayload() : Map.of();
            workspaceId = resolveWorkspaceId(envelope, payload);
            claimed = idempotencyService.claimIfNew(
                    tenantId,
                    workspaceId,
                    AppConstants.TOPIC_EMAIL_FAILED,
                    eventId,
                    idempotencyKey);
            if (!claimed) {
                return;
            }
            log.warn("Received failed email event: {}", envelope.getEventId());
            if (envelope.getRetryCount() < 3) {
                EventEnvelope<Map<String, Object>> retryEnvelope = envelope.forRetry();
                eventPublisher.publish(AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED, tenantId, retryEnvelope).join();
                log.info("Scheduled retry for failed email event [{}] attempt {}", envelope.getEventId(), retryEnvelope.getRetryCount());
            } else {
                eventPublisher.publish(AppConstants.TOPIC_EMAIL_FAILED_DLQ, tenantId, envelope).join();
                log.warn("Max retry reached for email event [{}], moved to DLQ", envelope.getEventId());
            }
            idempotencyService.markProcessed(tenantId, workspaceId, AppConstants.TOPIC_EMAIL_FAILED, eventId, idempotencyKey);
        } catch (InvalidFailedEmailEventException e) {
            if (claimed) {
                releaseClaim(tenantId, workspaceId, eventId, idempotencyKey, e);
            }
            log.warn("Rejecting invalid failed-email event for retry/DLQ. eventId={}, reason={}",
                    eventId(envelope), e.getMessage());
            throw new IllegalStateException("Invalid failed-email event", e);
        } catch (Exception e) {
            if (claimed) {
                releaseClaim(tenantId, workspaceId, eventId, idempotencyKey, e);
            }
            log.error("Error processing failed email event {}", eventId(envelope), e);
            throw new IllegalStateException("Failed to process failed email event " + eventId(envelope), e);
        }
    }

    private void releaseClaim(String tenantId,
                              String workspaceId,
                              String eventId,
                              String idempotencyKey,
                              Exception processingFailure) {
        try {
            idempotencyService.releaseClaim(tenantId, workspaceId, AppConstants.TOPIC_EMAIL_FAILED, eventId, idempotencyKey);
        } catch (Exception releaseFailure) {
            processingFailure.addSuppressed(releaseFailure);
            log.error("Failed to release failed-email idempotency claim eventId={}", eventId, releaseFailure);
        }
    }

    private String resolveWorkspaceId(EventEnvelope<Map<String, Object>> event, Map<String, Object> payload) {
        String envelopeWorkspaceId = normalize(event.getWorkspaceId());
        if (envelopeWorkspaceId != null) {
            return envelopeWorkspaceId;
        }
        Object fromPayload = payload.get("workspaceId");
        String payloadWorkspaceId = normalize(fromPayload);
        if (payloadWorkspaceId != null) {
            return payloadWorkspaceId;
        }
        throw new InvalidFailedEmailEventException("Missing workspaceId for failed-email event " + event.getEventId());
    }

    private String requireTenantId(EventEnvelope<Map<String, Object>> event) {
        String tenantId = normalize(event.getTenantId());
        if (tenantId != null) {
            return tenantId;
        }
        throw new InvalidFailedEmailEventException("Missing tenantId for failed-email event " + event.getEventId());
    }

    private void requireEventType(EventEnvelope<Map<String, Object>> event) {
        String eventType = normalize(event.getEventType());
        if (AppConstants.TOPIC_EMAIL_FAILED.equals(eventType)) {
            return;
        }
        if (eventType == null) {
            throw new InvalidFailedEmailEventException("Missing eventType for failed-email event " + event.getEventId());
        }
        throw new InvalidFailedEmailEventException("Unsupported failed-email event type: " + eventType);
    }

    private boolean hasClaimKey(String eventId, String idempotencyKey) {
        return eventId != null || idempotencyKey != null;
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String parsed = String.valueOf(value).trim();
        return parsed.isEmpty() ? null : parsed;
    }

    private String eventId(EventEnvelope<Map<String, Object>> event) {
        if (event == null || event.getEventId() == null || event.getEventId().isBlank()) {
            return "unknown";
        }
        return event.getEventId();
    }

    private static class InvalidFailedEmailEventException extends RuntimeException {
        private InvalidFailedEmailEventException(String message) {
            super(message);
        }
    }
}

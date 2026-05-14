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
                eventPublisher.publish(AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED, envelope.getTenantId(), retryEnvelope).join();
                log.info("Scheduled retry for failed email event [{}] attempt {}", envelope.getEventId(), retryEnvelope.getRetryCount());
            } else {
                eventPublisher.publish(AppConstants.TOPIC_EMAIL_FAILED_DLQ, envelope.getTenantId(), envelope).join();
                log.warn("Max retry reached for email event [{}], moved to DLQ", envelope.getEventId());
            }
            idempotencyService.markProcessed(tenantId, workspaceId, AppConstants.TOPIC_EMAIL_FAILED, eventId, idempotencyKey);
        } catch (Exception e) {
            if (claimed) {
                releaseClaim(tenantId, workspaceId, eventId, idempotencyKey, e);
            }
            log.error("Error processing failed email event", e);
            throw new RuntimeException(e);
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
        if (event.getWorkspaceId() != null && !event.getWorkspaceId().isBlank()) {
            return event.getWorkspaceId();
        }
        Object fromPayload = payload.get("workspaceId");
        if (fromPayload != null) {
            String parsed = String.valueOf(fromPayload).trim();
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }
        throw new IllegalArgumentException("Missing workspaceId for failed event " + event.getEventId());
    }
}

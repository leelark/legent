package com.legent.audience.event;

import com.legent.audience.service.SubscriberIntelligenceService;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudienceIntelligenceConsumer {

    private final SubscriberIntelligenceService intelligenceService;

    @KafkaListener(topics = AppConstants.TOPIC_TRACKING_INGESTED, groupId = "audience-tracking-group")
    public void consumeTracking(EventEnvelope<String> envelope) {
        try {
            String workspaceId = resolveWorkspace(envelope);
            String idempotencyKey = resolveIdempotencyKey(envelope);
            intelligenceService.applyTrackingIngested(
                    envelope.getTenantId(),
                    workspaceId,
                    envelope.getEventType(),
                    envelope.getEventId(),
                    idempotencyKey,
                    envelope.getPayload());
        } catch (Exception e) {
            log.error("Failed to process audience tracking intelligence event", e);
            throw new IllegalStateException("Failed to process audience tracking intelligence event", e);
        }
    }

    @KafkaListener(topics = {
            AppConstants.TOPIC_WORKFLOW_STARTED,
            AppConstants.TOPIC_WORKFLOW_STEP_COMPLETED,
            AppConstants.TOPIC_WORKFLOW_COMPLETED
    }, groupId = "audience-automation-group")
    public void consumeAutomation(EventEnvelope<String> envelope) {
        try {
            String workspaceId = resolveWorkspace(envelope);
            String idempotencyKey = resolveIdempotencyKey(envelope);
            intelligenceService.applyAutomationEvent(
                    envelope.getTenantId(),
                    workspaceId,
                    envelope.getEventType(),
                    envelope.getEventId(),
                    idempotencyKey,
                    envelope.getPayload());
        } catch (Exception e) {
            log.error("Failed to process audience automation intelligence event", e);
            throw new IllegalStateException("Failed to process audience automation intelligence event", e);
        }
    }

    private String resolveWorkspace(EventEnvelope<String> envelope) {
        requireEnvelope(envelope);
        String workspaceId = envelope.getWorkspaceId();
        if (workspaceId == null || workspaceId.isBlank()) {
            throw invalidEvent(envelope, "workspaceId");
        }
        return workspaceId.trim();
    }

    private String resolveIdempotencyKey(EventEnvelope<String> envelope) {
        String idempotencyKey = envelope.getIdempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw invalidEvent(envelope, "idempotencyKey");
        }
        return idempotencyKey.trim();
    }

    private void requireEnvelope(EventEnvelope<String> envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("Audience intelligence event envelope is required");
        }
    }

    private IllegalArgumentException invalidEvent(EventEnvelope<String> envelope, String fieldName) {
        return new IllegalArgumentException(String.format(
                "Audience intelligence event missing %s. eventId=%s, tenantId=%s, workspaceId=%s, eventType=%s",
                fieldName,
                envelope.getEventId(),
                envelope.getTenantId(),
                envelope.getWorkspaceId(),
                envelope.getEventType()));
    }
}

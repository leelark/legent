package com.legent.audience.event;

import com.legent.audience.service.SubscriberIntelligenceService;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudienceIntelligenceConsumer {

    private final SubscriberIntelligenceService intelligenceService;

    @KafkaListener(
            topics = AppConstants.TOPIC_TRACKING_INGESTED,
            groupId = "audience-tracking-group",
            containerFactory = "audienceTrackingIngestedKafkaListenerContainerFactory")
    public void consumeTrackingBatch(List<EventEnvelope<String>> envelopes) {
        if (envelopes == null || envelopes.isEmpty()) {
            log.info("Received empty audience tracking intelligence batch");
            return;
        }

        log.info("Received batch of {} audience tracking intelligence events", envelopes.size());
        Set<BatchLookupKey> seenEvents = new HashSet<>();
        for (EventEnvelope<String> envelope : envelopes) {
            try {
                AudienceTrackingEvent trackingEvent = resolveTrackingEvent(envelope);
                if (!seenEvents.add(trackingEvent.batchLookupKey())) {
                    log.debug("Skipping duplicate audience tracking intelligence event in batch eventId={}",
                            trackingEvent.eventId());
                    continue;
                }
                applyTracking(trackingEvent);
            } catch (IllegalArgumentException e) {
                log.warn("Skipping invalid audience tracking intelligence event eventId={}", eventId(envelope), e);
            } catch (Exception e) {
                log.error("Failed to process audience tracking intelligence event batch", e);
                throw new IllegalStateException("Failed to process audience tracking intelligence event batch", e);
            }
        }
    }

    public void consumeTracking(EventEnvelope<String> envelope) {
        try {
            processTracking(envelope);
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

    private void processTracking(EventEnvelope<String> envelope) {
        applyTracking(resolveTrackingEvent(envelope));
    }

    private void applyTracking(AudienceTrackingEvent trackingEvent) {
        intelligenceService.applyTrackingIngested(
                trackingEvent.tenantId(),
                trackingEvent.workspaceId(),
                trackingEvent.eventType(),
                trackingEvent.eventId(),
                trackingEvent.idempotencyKey(),
                trackingEvent.payload());
    }

    private AudienceTrackingEvent resolveTrackingEvent(EventEnvelope<String> envelope) {
        String workspaceId = resolveWorkspace(envelope);
        String tenantId = resolveTenant(envelope);
        String idempotencyKey = resolveIdempotencyKey(envelope);
        String eventType = resolveTrackingEventType(envelope);
        return new AudienceTrackingEvent(
                tenantId,
                workspaceId,
                eventType,
                envelope.getEventId(),
                idempotencyKey,
                envelope.getPayload());
    }

    private String resolveTenant(EventEnvelope<String> envelope) {
        String tenantId = envelope.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw invalidEvent(envelope, "tenantId");
        }
        return tenantId.trim();
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

    private String resolveTrackingEventType(EventEnvelope<String> envelope) {
        String eventType = envelope.getEventType();
        if (eventType == null || eventType.isBlank()) {
            throw invalidEvent(envelope, "eventType");
        }
        String trimmed = eventType.trim();
        if (!AppConstants.TOPIC_TRACKING_INGESTED.equals(trimmed)) {
            throw new IllegalArgumentException(String.format(
                    "Audience tracking intelligence event has unsupported eventType. eventId=%s, tenantId=%s, workspaceId=%s, eventType=%s",
                    envelope.getEventId(),
                    envelope.getTenantId(),
                    envelope.getWorkspaceId(),
                    envelope.getEventType()));
        }
        return trimmed;
    }

    private void requireEnvelope(EventEnvelope<String> envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("Audience intelligence event envelope is required");
        }
    }

    private String eventId(EventEnvelope<String> envelope) {
        return envelope == null ? "<null>" : envelope.getEventId();
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

    private record AudienceTrackingEvent(
            String tenantId,
            String workspaceId,
            String eventType,
            String eventId,
            String idempotencyKey,
            String payload) {

        BatchLookupKey batchLookupKey() {
            return new BatchLookupKey(tenantId, workspaceId, eventType, eventId, idempotencyKey);
        }
    }

    private record BatchLookupKey(
            String tenantId,
            String workspaceId,
            String eventType,
            String eventId,
            String idempotencyKey) {
    }
}

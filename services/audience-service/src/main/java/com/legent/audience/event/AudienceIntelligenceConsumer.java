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
            String workspaceId = resolveWorkspace(envelope.getWorkspaceId());
            intelligenceService.applyTrackingIngested(
                    envelope.getTenantId(),
                    workspaceId,
                    envelope.getEventType(),
                    envelope.getEventId(),
                    envelope.getIdempotencyKey(),
                    envelope.getPayload());
        } catch (Exception e) {
            log.error("Failed to process audience tracking intelligence event", e);
        }
    }

    @KafkaListener(topics = {
            AppConstants.TOPIC_WORKFLOW_STARTED,
            AppConstants.TOPIC_WORKFLOW_STEP_COMPLETED,
            AppConstants.TOPIC_WORKFLOW_COMPLETED
    }, groupId = "audience-automation-group")
    public void consumeAutomation(EventEnvelope<String> envelope) {
        try {
            String workspaceId = resolveWorkspace(envelope.getWorkspaceId());
            intelligenceService.applyAutomationEvent(
                    envelope.getTenantId(),
                    workspaceId,
                    envelope.getEventType(),
                    envelope.getEventId(),
                    envelope.getIdempotencyKey(),
                    envelope.getPayload());
        } catch (Exception e) {
            log.error("Failed to process audience automation intelligence event", e);
        }
    }

    private String resolveWorkspace(String workspaceId) {
        return (workspaceId == null || workspaceId.isBlank()) ? "workspace-default" : workspaceId;
    }
}

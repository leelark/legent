package com.legent.content.event;

import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentEventPublisher {

    private final EventPublisher eventPublisher;
    private static final String SOURCE = "content-service";

    public void publishTemplatePublished(String tenantId, String templateId, String templateName, String versionNumber) {
        ContentPublishedEvent payload = new ContentPublishedEvent(
                tenantId,
                templateId,
                templateName,
                versionNumber,
                java.time.Instant.now().toString()
        );
        EventEnvelope<ContentPublishedEvent> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_CONTENT_PUBLISHED,
                tenantId,
                SOURCE,
                payload
        );
        eventPublisher.publish(AppConstants.TOPIC_CONTENT_PUBLISHED, envelope);
    }

    public void publishTemplateSubmittedForApproval(String tenantId, String templateId, String templateName, int versionNumber) {
        java.util.Map<String, Object> payload = java.util.Map.of(
                "eventType", "TEMPLATE_SUBMITTED_FOR_APPROVAL",
                "templateId", templateId,
                "templateName", templateName,
                "versionNumber", versionNumber,
                "timestamp", java.time.Instant.now().toString()
        );
        EventEnvelope<java.util.Map<String, Object>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_CONTENT_PUBLISHED,
                tenantId,
                SOURCE,
                payload
        );
        eventPublisher.publish(AppConstants.TOPIC_CONTENT_PUBLISHED, envelope);
    }

    public void publishTemplateApproved(String tenantId, String templateId, String templateName, Integer versionNumber, String approvedBy) {
        java.util.Map<String, Object> payload = java.util.Map.of(
                "eventType", "TEMPLATE_APPROVED",
                "templateId", templateId,
                "templateName", templateName,
                "versionNumber", versionNumber,
                "approvedBy", approvedBy,
                "timestamp", java.time.Instant.now().toString()
        );
        EventEnvelope<java.util.Map<String, Object>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_CONTENT_PUBLISHED,
                tenantId,
                SOURCE,
                payload
        );
        eventPublisher.publish(AppConstants.TOPIC_CONTENT_PUBLISHED, envelope);
    }

    public void publishTemplateRejected(String tenantId, String templateId, String templateName, Integer versionNumber, String reason, String rejectedBy) {
        java.util.Map<String, Object> payload = java.util.Map.of(
                "eventType", "TEMPLATE_REJECTED",
                "templateId", templateId,
                "templateName", templateName,
                "versionNumber", versionNumber,
                "rejectionReason", reason,
                "rejectedBy", rejectedBy,
                "timestamp", java.time.Instant.now().toString()
        );
        EventEnvelope<java.util.Map<String, Object>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_CONTENT_PUBLISHED,
                tenantId,
                SOURCE,
                payload
        );
        eventPublisher.publish(AppConstants.TOPIC_CONTENT_PUBLISHED, envelope);
    }
}

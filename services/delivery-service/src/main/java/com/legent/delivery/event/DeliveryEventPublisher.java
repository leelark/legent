package com.legent.delivery.event;

import com.legent.common.constant.AppConstants;

import java.util.Map;

import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class DeliveryEventPublisher {

    private final EventPublisher eventPublisher;
    private static final String SOURCE = "delivery-service";

    public void publishEmailSent(String tenantId, String workspaceId, String messageId, String campaignId, String jobId, String batchId, String subscriberId) {
        String requiredWorkspace = requireWorkspace(workspaceId);
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_SENT, tenantId, SOURCE,
                Map.of(
                        "workspaceId", requiredWorkspace,
                        "messageId", safe(messageId, ""),
                        "campaignId", safe(campaignId, ""),
                        "jobId", safe(jobId, ""),
                        "batchId", safe(batchId, ""),
                        "subscriberId", safe(subscriberId, "")
                )
        );
        eventPublisher.publish(AppConstants.TOPIC_EMAIL_SENT, envelope);
    }

    public void publishEmailFailed(String tenantId, String workspaceId, String messageId, String campaignId, String jobId, String batchId, String subscriberId, String reason) {
        String requiredWorkspace = requireWorkspace(workspaceId);
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_FAILED, tenantId, SOURCE,
                Map.of(
                        "workspaceId", requiredWorkspace,
                        "messageId", safe(messageId, ""),
                        "campaignId", safe(campaignId, ""),
                        "jobId", safe(jobId, ""),
                        "batchId", safe(batchId, ""),
                        "subscriberId", safe(subscriberId, ""),
                        "reason", safe(reason, "unknown")
                )
        );
        eventPublisher.publish(AppConstants.TOPIC_EMAIL_FAILED, envelope);
    }

    public void publishRetryScheduled(String tenantId, String workspaceId, String messageId, long attemptCount, String nextRetryAt) {
        String requiredWorkspace = requireWorkspace(workspaceId);
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED, tenantId, SOURCE,
                Map.of(
                        "workspaceId", requiredWorkspace,
                        "messageId", safe(messageId, ""),
                        "attemptCount", String.valueOf(attemptCount),
                        "nextRetryAt", safe(nextRetryAt, "")
                )
        );
        eventPublisher.publish(AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED, envelope);
    }

    public void publishEmailBounced(String tenantId, String workspaceId, String email, String reason, String senderDomain) {
        String requiredWorkspace = requireWorkspace(workspaceId);
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_BOUNCED, tenantId, SOURCE,
                Map.of(
                        "workspaceId", requiredWorkspace,
                        "email", safe(email, ""),
                        "reason", safe(reason, "unknown"),
                        "type", "HARD_BOUNCE",
                        "senderDomain", safe(senderDomain, "")
                )
        );
        eventPublisher.publish(AppConstants.TOPIC_EMAIL_BOUNCED, envelope);
    }

    private String safe(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String requireWorkspace(String workspaceId) {
        String normalized = safe(workspaceId, null);
        if (normalized == null) {
            throw new IllegalArgumentException("workspaceId is required for delivery events");
        }
        return normalized;
    }
}

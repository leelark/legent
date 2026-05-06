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
        publishEmailSent(tenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, Map.of());
    }

    public void publishEmailSent(String tenantId, String workspaceId, String messageId, String campaignId, String jobId, String batchId, String subscriberId, Map<String, String> metadata) {
        String requiredWorkspace = requireWorkspace(workspaceId);
        Map<String, String> payload = new java.util.HashMap<>(metadata != null ? metadata : Map.of());
        payload.put("workspaceId", requiredWorkspace);
        payload.put("messageId", safe(messageId, ""));
        payload.put("campaignId", safe(campaignId, ""));
        payload.put("jobId", safe(jobId, ""));
        payload.put("batchId", safe(batchId, ""));
        payload.put("subscriberId", safe(subscriberId, ""));
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_SENT, tenantId, SOURCE,
                payload
        );
        eventPublisher.publish(AppConstants.TOPIC_EMAIL_SENT, envelope);
    }

    public void publishEmailFailed(String tenantId, String workspaceId, String messageId, String campaignId, String jobId, String batchId, String subscriberId, String reason) {
        publishEmailFailed(tenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, reason, Map.of());
    }

    public void publishEmailFailed(String tenantId, String workspaceId, String messageId, String campaignId, String jobId, String batchId, String subscriberId, String reason, Map<String, String> metadata) {
        String requiredWorkspace = requireWorkspace(workspaceId);
        Map<String, String> payload = new java.util.HashMap<>(metadata != null ? metadata : Map.of());
        payload.put("workspaceId", requiredWorkspace);
        payload.put("messageId", safe(messageId, ""));
        payload.put("campaignId", safe(campaignId, ""));
        payload.put("jobId", safe(jobId, ""));
        payload.put("batchId", safe(batchId, ""));
        payload.put("subscriberId", safe(subscriberId, ""));
        payload.put("reason", safe(reason, "unknown"));
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_FAILED, tenantId, SOURCE,
                payload
        );
        eventPublisher.publish(AppConstants.TOPIC_EMAIL_FAILED, envelope);
    }

    public void publishRetryScheduled(String tenantId, String workspaceId, String messageId, long attemptCount, String nextRetryAt) {
        publishRetryScheduled(tenantId, workspaceId, messageId, attemptCount, nextRetryAt, Map.of());
    }

    public void publishRetryScheduled(String tenantId, String workspaceId, String messageId, long attemptCount, String nextRetryAt, Map<String, String> metadata) {
        String requiredWorkspace = requireWorkspace(workspaceId);
        Map<String, String> payload = new java.util.HashMap<>(metadata != null ? metadata : Map.of());
        payload.put("workspaceId", requiredWorkspace);
        payload.put("messageId", safe(messageId, ""));
        payload.put("attemptCount", String.valueOf(attemptCount));
        payload.put("nextRetryAt", safe(nextRetryAt, ""));
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED, tenantId, SOURCE,
                payload
        );
        eventPublisher.publish(AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED, envelope);
    }

    public void publishEmailBounced(String tenantId, String workspaceId, String email, String reason, String senderDomain) {
        publishEmailBounced(tenantId, workspaceId, email, reason, senderDomain, Map.of());
    }

    public void publishEmailBounced(String tenantId, String workspaceId, String email, String reason, String senderDomain, Map<String, String> metadata) {
        String requiredWorkspace = requireWorkspace(workspaceId);
        Map<String, String> payload = new java.util.HashMap<>(metadata != null ? metadata : Map.of());
        payload.put("workspaceId", requiredWorkspace);
        payload.put("email", safe(email, ""));
        payload.put("reason", safe(reason, "unknown"));
        payload.put("type", "HARD_BOUNCE");
        payload.put("senderDomain", safe(senderDomain, ""));
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_BOUNCED, tenantId, SOURCE,
                payload
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

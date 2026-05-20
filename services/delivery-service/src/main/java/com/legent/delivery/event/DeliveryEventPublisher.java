package com.legent.delivery.event;

import com.legent.common.constant.AppConstants;
import com.legent.common.util.IdGenerator;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
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
        publishOrThrow(emailSentMessage(tenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, metadata));
    }

    public DeliveryFeedbackMessage emailSentMessage(String tenantId, String workspaceId, String messageId, String campaignId, String jobId, String batchId, String subscriberId) {
        return emailSentMessage(tenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, Map.of());
    }

    public DeliveryFeedbackMessage emailSentMessage(String tenantId, String workspaceId, String messageId, String campaignId, String jobId, String batchId, String subscriberId, Map<String, String> metadata) {
        String requiredWorkspace = requireWorkspace(workspaceId);
        Map<String, String> payload = new HashMap<>(metadata != null ? metadata : Map.of());
        payload.put("workspaceId", requiredWorkspace);
        payload.put("messageId", safe(messageId, ""));
        payload.put("campaignId", safe(campaignId, ""));
        payload.put("jobId", safe(jobId, ""));
        payload.put("batchId", safe(batchId, ""));
        payload.put("subscriberId", safe(subscriberId, ""));
        return feedbackMessage(AppConstants.TOPIC_EMAIL_SENT, tenantId, requiredWorkspace, payload,
                safe(messageId, "unknown") + ":SENT",
                safe(messageId, null),
                safe(messageId, null),
                safe(campaignId, null),
                safe(jobId, null),
                safe(batchId, null),
                safe(subscriberId, null),
                null,
                null);
    }

    public void publishEmailFailed(String tenantId, String workspaceId, String messageId, String campaignId, String jobId, String batchId, String subscriberId, String reason) {
        publishEmailFailed(tenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, reason, Map.of());
    }

    public void publishEmailFailed(String tenantId, String workspaceId, String messageId, String campaignId, String jobId, String batchId, String subscriberId, String reason, Map<String, String> metadata) {
        publishOrThrow(emailFailedMessage(tenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, reason, metadata));
    }

    public DeliveryFeedbackMessage emailFailedMessage(String tenantId, String workspaceId, String messageId, String campaignId, String jobId, String batchId, String subscriberId, String reason) {
        return emailFailedMessage(tenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, reason, Map.of());
    }

    public DeliveryFeedbackMessage emailFailedMessage(String tenantId, String workspaceId, String messageId, String campaignId, String jobId, String batchId, String subscriberId, String reason, Map<String, String> metadata) {
        String requiredWorkspace = requireWorkspace(workspaceId);
        Map<String, String> payload = new HashMap<>(metadata != null ? metadata : Map.of());
        payload.put("workspaceId", requiredWorkspace);
        payload.put("messageId", safe(messageId, ""));
        payload.put("campaignId", safe(campaignId, ""));
        payload.put("jobId", safe(jobId, ""));
        payload.put("batchId", safe(batchId, ""));
        payload.put("subscriberId", safe(subscriberId, ""));
        payload.put("reason", safe(reason, "unknown"));
        return feedbackMessage(AppConstants.TOPIC_EMAIL_FAILED, tenantId, requiredWorkspace, payload,
                safe(messageId, "unknown") + ":FAILED",
                safe(messageId, null),
                safe(messageId, null),
                safe(campaignId, null),
                safe(jobId, null),
                safe(batchId, null),
                safe(subscriberId, null),
                null,
                null);
    }

    public void publishRetryScheduled(String tenantId, String workspaceId, String messageId, long attemptCount, String nextRetryAt) {
        publishRetryScheduled(tenantId, workspaceId, messageId, attemptCount, nextRetryAt, Map.of());
    }

    public void publishRetryScheduled(String tenantId, String workspaceId, String messageId, long attemptCount, String nextRetryAt, Map<String, String> metadata) {
        publishOrThrow(retryScheduledMessage(tenantId, workspaceId, messageId, attemptCount, nextRetryAt, metadata));
    }

    public DeliveryFeedbackMessage retryScheduledMessage(String tenantId, String workspaceId, String messageId, long attemptCount, String nextRetryAt) {
        return retryScheduledMessage(tenantId, workspaceId, messageId, attemptCount, nextRetryAt, Map.of());
    }

    public DeliveryFeedbackMessage retryScheduledMessage(String tenantId, String workspaceId, String messageId, long attemptCount, String nextRetryAt, Map<String, String> metadata) {
        String requiredWorkspace = requireWorkspace(workspaceId);
        Map<String, String> payload = new HashMap<>(metadata != null ? metadata : Map.of());
        payload.put("workspaceId", requiredWorkspace);
        payload.put("messageId", safe(messageId, ""));
        payload.put("attemptCount", String.valueOf(attemptCount));
        payload.put("nextRetryAt", safe(nextRetryAt, ""));
        return feedbackMessage(AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED, tenantId, requiredWorkspace, payload,
                safe(messageId, "unknown") + ":RETRY:" + attemptCount + ":" + safe(nextRetryAt, "unknown"),
                safe(messageId, null),
                safe(messageId, null),
                safe(payload.get("campaignId"), null),
                safe(payload.get("jobId"), null),
                safe(payload.get("batchId"), null),
                safe(payload.get("subscriberId"), null),
                null,
                safe(payload.get("senderDomain"), null));
    }

    public void publishEmailBounced(String tenantId, String workspaceId, String email, String reason, String senderDomain) {
        publishEmailBounced(tenantId, workspaceId, email, reason, senderDomain, Map.of());
    }

    public void publishEmailBounced(String tenantId, String workspaceId, String email, String reason, String senderDomain, Map<String, String> metadata) {
        publishOrThrow(emailBouncedMessage(tenantId, workspaceId, email, reason, senderDomain, metadata));
    }

    public DeliveryFeedbackMessage emailBouncedMessage(String tenantId, String workspaceId, String email, String reason, String senderDomain) {
        return emailBouncedMessage(tenantId, workspaceId, email, reason, senderDomain, Map.of());
    }

    public DeliveryFeedbackMessage emailBouncedMessage(String tenantId, String workspaceId, String email, String reason, String senderDomain, Map<String, String> metadata) {
        String requiredWorkspace = requireWorkspace(workspaceId);
        Map<String, String> payload = new HashMap<>(metadata != null ? metadata : Map.of());
        payload.put("workspaceId", requiredWorkspace);
        payload.put("email", safe(email, ""));
        payload.put("reason", safe(reason, "unknown"));
        payload.put("type", "HARD_BOUNCE");
        payload.put("senderDomain", safe(senderDomain, ""));
        String messageId = safe(payload.get("messageId"), safe(email, "unknown"));
        return feedbackMessage(AppConstants.TOPIC_EMAIL_BOUNCED, tenantId, requiredWorkspace, payload,
                messageId + ":BOUNCED",
                safe(senderDomain, safe(email, "unknown")),
                messageId,
                safe(payload.get("campaignId"), null),
                safe(payload.get("jobId"), null),
                safe(payload.get("batchId"), null),
                safe(payload.get("subscriberId"), null),
                safe(email, null),
                safe(senderDomain, null));
    }

    public void publishOrThrow(DeliveryFeedbackMessage message) {
        eventPublisher.publish(message.topic(), message.partitionKey(), message.envelope()).join();
    }

    private DeliveryFeedbackMessage feedbackMessage(String topic,
                                                    String tenantId,
                                                    String workspaceId,
                                                    Map<String, String> payload,
                                                    String transitionKey,
                                                    String partitionKey,
                                                    String messageId,
                                                    String campaignId,
                                                    String jobId,
                                                    String batchId,
                                                    String subscriberId,
                                                    String recipientEmail,
                                                    String senderDomain) {
        String normalizedTenantId = safe(tenantId, TenantContext.getTenantId());
        String requiredTenantId = safe(normalizedTenantId, "UNKNOWN");
        String normalizedTransitionKey = safe(transitionKey, topic + ":" + IdGenerator.newId());
        String identity = requiredTenantId + "|" + workspaceId + "|" + topic + "|" + normalizedTransitionKey;
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.<Map<String, String>>builder()
                .eventId(stableUuid("delivery-feedback-event:" + identity))
                .eventType(topic)
                .timestamp(Instant.now())
                .tenantId(requiredTenantId)
                .workspaceId(workspaceId)
                .environmentId(TenantContext.getEnvironmentId())
                .actorId(TenantContext.getUserId())
                .ownershipScope("WORKSPACE")
                .correlationId(safe(TenantContext.getCorrelationId(), IdGenerator.newCorrelationId()))
                .source(SOURCE)
                .schemaVersion(1)
                .idempotencyKey("IK-" + stableUuid("delivery-feedback-idempotency:" + identity))
                .retryCount(0)
                .payload(Map.copyOf(payload))
                .build();
        return new DeliveryFeedbackMessage(
                topic,
                normalizedTransitionKey,
                safe(partitionKey, safe(messageId, envelope.getEventId())),
                safe(messageId, safe(partitionKey, envelope.getEventId())),
                campaignId,
                jobId,
                batchId,
                subscriberId,
                recipientEmail,
                senderDomain,
                envelope);
    }

    private String stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
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

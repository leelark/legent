package com.legent.campaign.event;

import com.legent.common.constant.AppConstants;
import com.legent.security.TenantContext;

import java.util.Map;

import java.time.Instant;
import java.util.concurrent.CompletionException;

import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;


@Component
@RequiredArgsConstructor
public class CampaignEventPublisher {

    private final EventPublisher eventPublisher;
    private static final String SOURCE = "campaign-service";

    public void publishSendRequested(String tenantId, String campaignId, String jobId, Instant scheduledAt) {
        String workspaceId = TenantContext.requireWorkspaceId();
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_SEND_REQUESTED, tenantId, SOURCE,
                Map.of(
                        "campaignId", campaignId,
                        "jobId", jobId,
                        "workspaceId", workspaceId,
                        "scheduledAt", scheduledAt != null ? scheduledAt.toString() : ""
                )
        );
        publishAndAwait(AppConstants.TOPIC_SEND_REQUESTED, envelope);
    }

    public void publishAudienceResolutionRequested(String tenantId, String campaignId, String jobId, List<Map<String, String>> audiences) {
        String workspaceId = TenantContext.requireWorkspaceId();
        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED, tenantId, SOURCE,
                Map.of(
                        "campaignId", campaignId,
                        "jobId", jobId,
                        "workspaceId", workspaceId,
                        "audiences", audiences
                )
        );
        publishAndAwait(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED, envelope);
    }

    public void publishBatchCreated(String tenantId, String jobId, String batchId) {
        String workspaceId = TenantContext.requireWorkspaceId();
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_BATCH_CREATED, tenantId, SOURCE,
                Map.of(
                        "jobId", jobId,
                        "batchId", batchId,
                        "workspaceId", workspaceId
                )
        );
        publishAndAwait(AppConstants.TOPIC_BATCH_CREATED, envelope);
    }

    public void publishSendProcessing(String tenantId, String jobId, String batchId, String payloadJson) {
        EventEnvelope<String> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_SEND_PROCESSING, tenantId, SOURCE, payloadJson
        );
        // Using batchId as key for partition locality for the same batch
        publishAndAwait(AppConstants.TOPIC_SEND_PROCESSING, batchId, envelope);
    }

    public void publishJobStatus(String topic, String tenantId, String jobId, String payloadJson) {
        EventEnvelope<String> envelope = EventEnvelope.wrap(topic, tenantId, SOURCE, payloadJson);
        publishAndAwait(topic, envelope);
    }

    private <T> void publishAndAwait(String topic, EventEnvelope<T> envelope) {
        try {
            eventPublisher.publish(topic, envelope).join();
        } catch (CompletionException e) {
            throw publishFailure(topic, e);
        }
    }

    private <T> void publishAndAwait(String topic, String key, EventEnvelope<T> envelope) {
        try {
            eventPublisher.publish(topic, key, envelope).join();
        } catch (CompletionException e) {
            throw publishFailure(topic, e);
        }
    }

    private RuntimeException publishFailure(String topic, CompletionException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Failed to publish event to " + topic, cause);
    }
}

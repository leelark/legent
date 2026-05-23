package com.legent.campaign.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.dto.CampaignDto;
import com.legent.common.constant.AppConstants;
import com.legent.campaign.service.BatchingService;
import com.legent.campaign.service.CampaignEventIdempotencyService;
import com.legent.campaign.service.CampaignMetricsService;
import com.legent.campaign.service.CampaignSendSafetyService;
import com.legent.campaign.service.CampaignStateMachineService;
import com.legent.campaign.service.OrchestrationService;
import com.legent.campaign.service.SendExecutionService;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendBatchRepository;
import com.legent.campaign.repository.SendJobRepository;
import com.legent.kafka.model.EventEnvelope;
import com.legent.security.TenantContext;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignEventConsumer {

    private final BatchingService batchingService;
    private final SendExecutionService executionService;
    private final ObjectMapper objectMapper;
    private final OrchestrationService orchestrationService;
    private final CampaignEventIdempotencyService idempotencyService;
    private final SendJobRepository sendJobRepository;
    private final SendBatchRepository sendBatchRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignStateMachineService stateMachine;
    private final CampaignSendSafetyService sendSafetyService;
    private final CampaignMetricsService metricsService;

    @KafkaListener(topics = AppConstants.TOPIC_AUDIENCE_RESOLVED, groupId = AppConstants.GROUP_CAMPAIGN)
    public void handleAudienceResolved(EventEnvelope<Map<String, Object>> event) {
        EventRegistration registration = null;
        boolean sideEffectsComplete = false;
        try {
            Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Collections.emptyMap();
            String workspaceId = requireWorkspaceId(event, payload, AppConstants.TOPIC_AUDIENCE_RESOLVED);
            requirePayloadString(payload, "campaignId", AppConstants.TOPIC_AUDIENCE_RESOLVED, event);
            String jobId = requirePayloadString(payload, "jobId", AppConstants.TOPIC_AUDIENCE_RESOLVED, event);
            String chunkIdentity = audienceChunkIdentity(event, payload);
            registration = registerEvent(event, AppConstants.TOPIC_AUDIENCE_RESOLVED, workspaceId, chunkIdentity, chunkIdentity);
            if (!registration.claimed()) {
                return;
            }
            applyTenantContext(event, workspaceId);
            boolean isLastChunk = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("isLastChunk", true)));
            
            // Re-serialize and deserialize to get typed list
            List<Map<String, String>> subscribers = objectMapper.convertValue(
                    payload.get("subscribers"), new TypeReference<>() {}
            );
            if (subscribers == null) {
                subscribers = List.of();
            }

            log.info("Received resolved audience chunk for job {}. Size: {}, isLast: {}", jobId, subscribers.size(), isLastChunk);
            batchingService.processResolvedAudienceChunk(event.getTenantId(), workspaceId, jobId, subscribers, isLastChunk);
            sideEffectsComplete = true;
            completeEvent(registration);

        } catch (Exception e) {
            releaseEventClaim(registration, sideEffectsComplete);
            log.error("Failed handling TOPIC_AUDIENCE_RESOLVED {}", eventId(event), e);
            throw new IllegalStateException("Failed handling TOPIC_AUDIENCE_RESOLVED", e);
        } finally {
            TenantContext.clear();
        }
    }

    @KafkaListener(topics = AppConstants.TOPIC_SEND_PROCESSING, groupId = AppConstants.GROUP_CAMPAIGN, concurrency = "3")
    public void handleSendProcessing(EventEnvelope<?> event) {
        EventRegistration registration = null;
        boolean sideEffectsComplete = false;
        try {
            Map<String, Object> payload = payloadAsMap(event.getPayload());
            String workspaceId = requireWorkspaceId(event, payload, AppConstants.TOPIC_SEND_PROCESSING);
            String jobId = requirePayloadString(payload, "jobId", AppConstants.TOPIC_SEND_PROCESSING, event);
            String batchId = requirePayloadString(payload, "batchId", AppConstants.TOPIC_SEND_PROCESSING, event);
            registration = registerEvent(event, AppConstants.TOPIC_SEND_PROCESSING, workspaceId);
            if (!registration.claimed()) {
                return;
            }
            applyTenantContext(event, workspaceId);
            executionService.executeBatch(event.getTenantId(), jobId, batchId, null);
            sideEffectsComplete = true;
            completeEvent(registration);
        } catch (Exception e) {
            releaseEventClaim(registration, sideEffectsComplete);
            log.error("Failed handling TOPIC_SEND_PROCESSING {}", eventId(event), e);
            throw new IllegalStateException("Failed handling TOPIC_SEND_PROCESSING", e);
        } finally {
            TenantContext.clear();
        }
    }

    @KafkaListener(topics = AppConstants.TOPIC_BATCH_CREATED, groupId = AppConstants.GROUP_CAMPAIGN, concurrency = "3")
    public void handleBatchCreated(EventEnvelope<Map<String, String>> event) {
        EventRegistration registration = null;
        boolean sideEffectsComplete = false;
        try {
            Map<String, String> payload = event.getPayload() != null ? event.getPayload() : Collections.emptyMap();
            String workspaceId = requireWorkspaceId(event, payload, AppConstants.TOPIC_BATCH_CREATED);
            String jobId = requirePayloadString(payload, "jobId", AppConstants.TOPIC_BATCH_CREATED, event);
            String batchId = requirePayloadString(payload, "batchId", AppConstants.TOPIC_BATCH_CREATED, event);
            registration = registerEvent(event, AppConstants.TOPIC_BATCH_CREATED, workspaceId);
            if (!registration.claimed()) {
                return;
            }
            applyTenantContext(event, workspaceId);

            // To pass payload we fetch from DB inside the service
            // This is a common pattern to avoid Kafka message size limits
            executionService.executeBatch(event.getTenantId(), jobId, batchId, null); // passing null as it will fetch from db
            sideEffectsComplete = true;
            completeEvent(registration);

        } catch (Exception e) {
            releaseEventClaim(registration, sideEffectsComplete);
            log.error("Failed handling TOPIC_BATCH_CREATED {}", eventId(event), e);
            throw new IllegalStateException("Failed handling TOPIC_BATCH_CREATED", e);
        } finally {
            TenantContext.clear();
        }
    }

    @KafkaListener(topics = AppConstants.TOPIC_SEND_REQUESTED, groupId = AppConstants.GROUP_CAMPAIGN, concurrency = "2")
    public void handleAutomationSendRequest(EventEnvelope<?> event) {
        EventRegistration registration = null;
        boolean sideEffectsComplete = false;
        try {
            Map<String, Object> payload = payloadAsMap(event.getPayload());
            String campaignId = requirePayloadString(payload, "campaignId", AppConstants.TOPIC_SEND_REQUESTED, event);
            String workspaceId = requireWorkspaceId(event, payload, AppConstants.TOPIC_SEND_REQUESTED);
            requireConfirmedLaunch(payload, AppConstants.TOPIC_SEND_REQUESTED, event);
            String idempotencyKey = requireSendRequestIdempotencyKey(payload, AppConstants.TOPIC_SEND_REQUESTED, event);
            requireCampaignHandoffBoundary(payload, AppConstants.TOPIC_SEND_REQUESTED, event);
            registration = registerEvent(event, AppConstants.TOPIC_SEND_REQUESTED, workspaceId, event.getEventId(), idempotencyKey);
            if (!registration.claimed()) {
                return;
            }
            applyTenantContext(event, workspaceId);

            CampaignDto.TriggerLaunchRequest request = CampaignDto.TriggerLaunchRequest.builder()
                    .triggerSource(stringValue(payload.getOrDefault("triggerSource", "AUTOMATION")))
                    .triggerReference(stringValue(payload.getOrDefault("triggerReference", payload.get("instanceId"))))
                    .idempotencyKey(idempotencyKey)
                    .scheduledAt(instantValue(payload.get("scheduledAt"), AppConstants.TOPIC_SEND_REQUESTED, event))
                    .metadata(sendRequestMetadata(payload, event))
                    .build();
            orchestrationService.triggerFromAutomation(campaignId, request);
            sideEffectsComplete = true;
            completeEvent(registration);
        } catch (Exception e) {
            releaseEventClaim(registration, sideEffectsComplete);
            log.error("Failed handling TOPIC_SEND_REQUESTED {}", eventId(event), e);
            throw new IllegalStateException("Failed handling TOPIC_SEND_REQUESTED", e);
        } finally {
            TenantContext.clear();
        }
    }

    private Map<String, Object> payloadAsMap(Object payload) {
        if (payload == null) {
            return Collections.emptyMap();
        }
        if (payload instanceof String json) {
            if (json.isBlank()) {
                return Collections.emptyMap();
            }
            try {
                return objectMapper.readValue(json, new TypeReference<>() {});
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON event payload", e);
            }
        }
        if (payload instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, new TypeReference<>() {});
        }
        throw new IllegalArgumentException("Unsupported event payload type: " + payload.getClass().getName());
    }

    @KafkaListener(topics = AppConstants.TOPIC_EMAIL_SENT, groupId = AppConstants.GROUP_CAMPAIGN, concurrency = "5")
    @Transactional
    public void handleEmailSent(EventEnvelope<Map<String, Object>> event) {
        reconcileDeliveryFeedback(event, false);
    }

    @KafkaListener(topics = AppConstants.TOPIC_EMAIL_FAILED, groupId = AppConstants.GROUP_CAMPAIGN, concurrency = "5")
    @Transactional
    public void handleEmailFailed(EventEnvelope<Map<String, Object>> event) {
        reconcileDeliveryFeedback(event, true);
    }

    @KafkaListener(topics = AppConstants.TOPIC_TRACKING_INGESTED, groupId = AppConstants.GROUP_CAMPAIGN + "-experiment-metrics", concurrency = "3")
    public void handleTrackingIngested(EventEnvelope<String> event) {
        EventRegistration registration = null;
        boolean sideEffectsComplete = false;
        try {
            if (event.getPayload() == null || event.getPayload().isBlank()) {
                return;
            }
            Map<String, Object> payload = objectMapper.readValue(event.getPayload(), new TypeReference<>() {});
            String workspaceId = resolveWorkspaceId(event, payload);
            if (workspaceId == null) {
                return;
            }
            registration = registerEvent(event, AppConstants.TOPIC_TRACKING_INGESTED, workspaceId);
            if (!registration.claimed()) {
                return;
            }
            String campaignId = stringValue(payload.get("campaignId"));
            String experimentId = stringValue(payload.get("experimentId"));
            if (campaignId == null || experimentId == null) {
                sideEffectsComplete = true;
                completeEvent(registration);
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = payload.get("metadata") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Collections.emptyMap();
            metricsService.recordTracking(
                    event.getTenantId(),
                    workspaceId,
                    campaignId,
                    experimentId,
                    stringValue(payload.get("variantId")),
                    stringValue(payload.get("eventType")),
                    metadata);
            sideEffectsComplete = true;
            completeEvent(registration);
        } catch (Exception e) {
            releaseEventClaim(registration, sideEffectsComplete);
            log.error("Failed handling TOPIC_TRACKING_INGESTED {}", eventId(event), e);
            throw new IllegalStateException("Failed handling TOPIC_TRACKING_INGESTED", e);
        }
    }

    private void reconcileDeliveryFeedback(EventEnvelope<Map<String, Object>> event, boolean failed) {
        EventRegistration registration = null;
        boolean sideEffectsComplete = false;
        try {
            Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Collections.emptyMap();
            String topic = failed ? AppConstants.TOPIC_EMAIL_FAILED : AppConstants.TOPIC_EMAIL_SENT;
            String workspaceId = requireWorkspaceId(event, payload, topic);
            registration = registerEvent(event, topic, workspaceId);
            if (!registration.claimed()) {
                return;
            }
            applyTenantContext(event, workspaceId);

            SendJob job = findSendJob(event.getTenantId(), workspaceId, payload);
            if (job == null) {
                log.warn("No send job found for delivery feedback event {}", event.getEventId());
                sideEffectsComplete = true;
                completeEvent(registration);
                return;
            }

            String batchId = stringValue(payload.get("batchId"));
            if (batchId != null) {
                sendBatchRepository.applyDeliveryFeedbackCounters(
                        event.getTenantId(),
                        workspaceId,
                        batchId,
                        failed,
                        stringValue(payload.get("reason")),
                        SendBatch.BatchStatus.PENDING,
                        SendBatch.BatchStatus.PROCESSING,
                        SendBatch.BatchStatus.COMPLETED,
                        SendBatch.BatchStatus.FAILED,
                        SendBatch.BatchStatus.PARTIAL,
                        Instant.now());
            }

            int jobRowsUpdated;
            String reason = stringValue(payload.get("reason"));
            if (failed) {
                jobRowsUpdated = sendJobRepository.incrementFailedFeedbackCounter(
                        event.getTenantId(),
                        workspaceId,
                        job.getId(),
                        reason,
                        Instant.now());
            } else {
                jobRowsUpdated = sendJobRepository.incrementSentFeedbackCounter(
                        event.getTenantId(),
                        workspaceId,
                        job.getId(),
                        Instant.now());
            }
            if (jobRowsUpdated == 0) {
                log.warn("Send job disappeared before delivery feedback counters were updated. eventId={}, jobId={}",
                        event.getEventId(), job.getId());
                sideEffectsComplete = true;
                completeEvent(registration);
                return;
            }
            String messageId = stringValue(payload.get("messageId"));
            if (messageId != null) {
                sendSafetyService.recordDeliveryFeedback(
                        event.getTenantId(),
                        workspaceId,
                        messageId,
                        failed,
                        reason);
            }

            sendJobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                    event.getTenantId(),
                    workspaceId,
                    job.getId()).ifPresent(this::reconcileJobAndCampaignState);
            sideEffectsComplete = true;
            completeEvent(registration);
        } catch (Exception e) {
            releaseEventClaim(registration, sideEffectsComplete);
            log.error("Failed reconciling delivery feedback {}", eventId(event), e);
            throw new IllegalStateException("Failed reconciling delivery feedback", e);
        } finally {
            TenantContext.clear();
        }
    }

    private void reconcileJobAndCampaignState(SendJob job) {
        long totalBatches = sendBatchRepository.countByTenantWorkspaceAndJob(job.getTenantId(), job.getWorkspaceId(), job.getId());
        if (totalBatches == 0) {
            return;
        }
        long activeBatches = sendBatchRepository.countByTenantWorkspaceAndJobAndStatuses(
                job.getTenantId(),
                job.getWorkspaceId(),
                job.getId(),
                List.of(SendBatch.BatchStatus.PENDING, SendBatch.BatchStatus.PROCESSING, SendBatch.BatchStatus.PARTIAL)
        );
        if (activeBatches > 0) {
            if (job.getStatus() == SendJob.JobStatus.BATCHING) {
                stateMachine.transitionJob(job, SendJob.JobStatus.SENDING, "Batch execution in progress");
                sendJobRepository.save(job);
            }
            return;
        }

        if (job.getStatus() == SendJob.JobStatus.CANCELLED || job.getStatus() == SendJob.JobStatus.COMPLETED || job.getStatus() == SendJob.JobStatus.FAILED) {
            return;
        }

        if ((job.getTotalFailed() != null && job.getTotalFailed() > 0)) {
            stateMachine.transitionJob(job, SendJob.JobStatus.FAILED, "Delivery reported failed recipients");
        } else {
            stateMachine.transitionJob(job, SendJob.JobStatus.COMPLETED, "All batches processed");
        }
        sendJobRepository.save(job);

        campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                job.getTenantId(),
                job.getWorkspaceId(),
                job.getCampaignId()
        ).ifPresent(campaign -> {
            if (campaign.getStatus() == Campaign.CampaignStatus.CANCELLED || campaign.getStatus() == Campaign.CampaignStatus.ARCHIVED) {
                return;
            }
            if (job.getStatus() == SendJob.JobStatus.FAILED) {
                stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.FAILED, job.getErrorMessage());
            } else {
                stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.COMPLETED, "Campaign send completed");
            }
            campaignRepository.save(campaign);
        });
    }

    private SendJob findSendJob(String tenantId, String workspaceId, Map<String, Object> payload) {
        String jobId = stringValue(payload.get("jobId"));
        if (jobId != null) {
            return sendJobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, jobId).orElse(null);
        }
        String campaignId = stringValue(payload.get("campaignId"));
        if (campaignId == null) {
            return null;
        }
        return sendJobRepository.findFirstByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                tenantId,
                workspaceId,
                campaignId
        ).orElse(null);
    }

    private EventRegistration registerEvent(EventEnvelope<?> event, String eventType, String workspaceId) {
        return registerEvent(event, eventType, workspaceId, event.getEventId(), event.getIdempotencyKey());
    }

    private EventRegistration registerEvent(EventEnvelope<?> event,
                                            String eventType,
                                            String workspaceId,
                                            String eventId,
                                            String idempotencyKey) {
        String tenantId = requireTenantId(event, eventType);
        boolean claimed = idempotencyService.registerIfNew(
                tenantId,
                workspaceId,
                eventType,
                eventId,
                idempotencyKey
        );
        return new EventRegistration(
                tenantId,
                workspaceId,
                eventType,
                eventId,
                idempotencyKey,
                claimed
        );
    }

    private String requireTenantId(EventEnvelope<?> event, String topic) {
        String tenantId = event == null ? null : stringValue(event.getTenantId());
        if (tenantId != null) {
            return tenantId;
        }
        throw new IllegalArgumentException("tenantId is required for " + topic + " event " + eventId(event));
    }

    private void completeEvent(EventRegistration registration) {
        if (registration == null || !registration.claimed()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    markProcessed(registration);
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        releaseClaim(registration);
                    }
                }
            });
            return;
        }
        markProcessed(registration);
    }

    private void releaseEventClaim(EventRegistration registration, boolean sideEffectsComplete) {
        if (registration == null || !registration.claimed() || sideEffectsComplete) {
            return;
        }
        releaseClaim(registration);
    }

    private void markProcessed(EventRegistration registration) {
        idempotencyService.markProcessed(
                registration.tenantId(),
                registration.workspaceId(),
                registration.eventType(),
                registration.eventId(),
                registration.idempotencyKey());
    }

    private void releaseClaim(EventRegistration registration) {
        idempotencyService.releaseClaim(
                registration.tenantId(),
                registration.workspaceId(),
                registration.eventType(),
                registration.eventId(),
                registration.idempotencyKey());
    }

    private String resolveWorkspaceId(EventEnvelope<?> event, Map<String, ?> payload) {
        if (event.getWorkspaceId() != null && !event.getWorkspaceId().isBlank()) {
            return event.getWorkspaceId();
        }
        if (payload != null) {
            String fromPayload = stringValue(payload.get("workspaceId"));
            if (fromPayload != null) {
                return fromPayload;
            }
        }
        log.error("Dropping campaign event without workspaceId. eventId={}, eventType={}", event.getEventId(), event.getEventType());
        return null;
    }

    private String requireWorkspaceId(EventEnvelope<?> event, Map<String, ?> payload, String topic) {
        String workspaceId = resolveWorkspaceId(event, payload);
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required for " + topic + " event " + eventId(event));
        }
        return workspaceId;
    }

    private String requirePayloadString(Map<String, ?> payload, String fieldName, String topic, EventEnvelope<?> event) {
        String value = payload == null ? null : stringValue(payload.get(fieldName));
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required for " + topic + " event " + eventId(event));
        }
        return value;
    }

    private void requireConfirmedLaunch(Map<String, ?> payload, String topic, EventEnvelope<?> event) {
        Object value = payload == null ? null : payload.get("confirmLaunch");
        boolean confirmed = value instanceof Boolean booleanValue
                ? booleanValue
                : "true".equalsIgnoreCase(stringValue(value));
        if (!confirmed) {
            throw new IllegalArgumentException("confirmLaunch=true is required for " + topic + " event " + eventId(event));
        }
    }

    private String requireSendRequestIdempotencyKey(Map<String, ?> payload, String topic, EventEnvelope<?> event) {
        String payloadKey = payload == null ? null : stringValue(payload.get("idempotencyKey"));
        String envelopeKey = stringValue(event.getIdempotencyKey());
        if (payloadKey != null && envelopeKey != null && !payloadKey.equals(envelopeKey)) {
            throw new IllegalArgumentException("idempotencyKey mismatch between envelope and payload for " + topic + " event " + eventId(event));
        }
        String idempotencyKey = payloadKey != null ? payloadKey : envelopeKey;
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("idempotencyKey is required for " + topic + " event " + eventId(event));
        }
        return idempotencyKey;
    }

    private void requireCampaignHandoffBoundary(Map<String, ?> payload, String topic, EventEnvelope<?> event) {
        if (payload == null || stringValue(payload.get("subscriberId")) == null) {
            return;
        }
        String handoffBoundary = stringValue(payload.get("handoffBoundary"));
        String sendLifecycleOwner = stringValue(payload.get("sendLifecycleOwner"));
        if (!"CAMPAIGN_ORCHESTRATION".equals(handoffBoundary)
                || !isTrue(payload.get("requiresCampaignPreflight"))
                || !"campaign-service".equals(sendLifecycleOwner)) {
            throw new IllegalArgumentException(
                    "subscriberId on " + topic + " requires campaign orchestration handoff markers for event " + eventId(event));
        }
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return "true".equalsIgnoreCase(stringValue(value));
    }

    private Instant instantValue(Object value, String topic, EventEnvelope<?> event) {
        String raw = stringValue(value);
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception ex) {
            throw new IllegalArgumentException("scheduledAt must be an ISO-8601 instant for " + topic + " event " + eventId(event));
        }
    }

    private Map<String, Object> sendRequestMetadata(Map<String, Object> payload, EventEnvelope<?> event) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        for (String key : List.of(
                "workflowId",
                "workflowVersion",
                "workflowInstanceId",
                "instanceId",
                "nodeId",
                "subscriberId",
                "activityId",
                "activityRunId",
                "activityType",
                "traceId",
                "handoffBoundary",
                "requiresCampaignPreflight",
                "sendLifecycleOwner")) {
            Object value = payload.get(key);
            if (value != null) {
                metadata.put(key, value);
            }
        }
        metadata.put("sourceEventId", event.getEventId());
        metadata.put("source", event.getSource());
        return metadata;
    }

    private void applyTenantContext(EventEnvelope<?> event, String workspaceId) {
        TenantContext.setTenantId(event.getTenantId());
        TenantContext.setWorkspaceId(workspaceId);
        if (event.getEnvironmentId() != null && !event.getEnvironmentId().isBlank()) {
            TenantContext.setEnvironmentId(event.getEnvironmentId());
        }
        if (event.getCorrelationId() != null && !event.getCorrelationId().isBlank()) {
            TenantContext.setCorrelationId(event.getCorrelationId());
        }
    }

    private String audienceChunkIdentity(EventEnvelope<?> event, Map<String, ?> payload) {
        String chunkId = stringValue(payload.get("chunkId"));
        if (chunkId != null) {
            return chunkId;
        }
        String jobId = stringValue(payload.get("jobId"));
        String chunkIndex = stringValue(payload.get("chunkIndex"));
        if (jobId != null && chunkIndex != null) {
            return jobId + ":chunk:" + chunkIndex;
        }
        return event.getEventId();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String eventId(EventEnvelope<?> event) {
        return event == null ? "unknown" : event.getEventId();
    }

    private record EventRegistration(String tenantId,
                                     String workspaceId,
                                     String eventType,
                                     String eventId,
                                     String idempotencyKey,
                                     boolean claimed) {
    }
}

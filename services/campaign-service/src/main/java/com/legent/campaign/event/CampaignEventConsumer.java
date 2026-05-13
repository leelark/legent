package com.legent.campaign.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.dto.CampaignDto;
import com.legent.common.constant.AppConstants;
import java.util.List;

import java.util.Map;

import com.legent.campaign.service.BatchingService;
import com.legent.campaign.service.CampaignEventIdempotencyService;
import com.legent.campaign.service.CampaignStateMachineService;
import com.legent.campaign.service.OrchestrationService;
import com.legent.campaign.service.CampaignMetricsService;
import com.legent.campaign.service.CampaignSendSafetyService;
import com.legent.campaign.service.SendExecutionService;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendBatchRepository;
import com.legent.campaign.repository.SendJobRepository;
import com.legent.kafka.model.EventEnvelope;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;


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
        try {
            Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Collections.emptyMap();
            String workspaceId = resolveWorkspaceId(event, payload);
            if (workspaceId == null) {
                return;
            }
            if (!registerEvent(event, AppConstants.TOPIC_AUDIENCE_RESOLVED, workspaceId)) {
                return;
            }
            TenantContext.setTenantId(event.getTenantId());
            TenantContext.setWorkspaceId(workspaceId);
            String jobId = (String) payload.get("jobId");
            boolean isLastChunk = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("isLastChunk", true)));
            
            // Re-serialize and deserialize to get typed list
            List<Map<String, String>> subscribers = objectMapper.convertValue(
                    payload.get("subscribers"), new TypeReference<>() {}
            );
            if (subscribers == null) {
                subscribers = List.of();
            }

            log.info("Received resolved audience chunk for job {}. Size: {}, isLast: {}", jobId, subscribers.size(), isLastChunk);
            batchingService.processResolvedAudienceChunk(event.getTenantId(), jobId, subscribers, isLastChunk);

        } catch (Exception e) {
            log.error("Failed handling TOPIC_AUDIENCE_RESOLVED {}", eventId(event), e);
            throw new IllegalStateException("Failed handling TOPIC_AUDIENCE_RESOLVED", e);
        } finally {
            TenantContext.clear();
        }
    }

    @KafkaListener(topics = AppConstants.TOPIC_SEND_PROCESSING, groupId = AppConstants.GROUP_CAMPAIGN, concurrency = "3")
    public void handleSendProcessing(EventEnvelope<Map<String, Object>> event) {
        try {
            Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Collections.emptyMap();
            String workspaceId = resolveWorkspaceId(event, payload);
            if (workspaceId == null) {
                return;
            }
            if (!registerEvent(event, AppConstants.TOPIC_SEND_PROCESSING, workspaceId)) {
                return;
            }
            TenantContext.setTenantId(event.getTenantId());
            TenantContext.setWorkspaceId(workspaceId);
            String jobId = String.valueOf(payload.getOrDefault("jobId", ""));
            String batchId = String.valueOf(payload.getOrDefault("batchId", ""));
            if (!jobId.isBlank() && !batchId.isBlank()) {
                executionService.executeBatch(event.getTenantId(), jobId, batchId, null);
            } else {
                log.warn("SEND_PROCESSING event {} missing jobId/batchId - ignored", event.getEventId());
            }
        } catch (Exception e) {
            log.error("Failed handling TOPIC_SEND_PROCESSING {}", eventId(event), e);
            throw new IllegalStateException("Failed handling TOPIC_SEND_PROCESSING", e);
        } finally {
            TenantContext.clear();
        }
    }

    @KafkaListener(topics = AppConstants.TOPIC_BATCH_CREATED, groupId = AppConstants.GROUP_CAMPAIGN, concurrency = "3")
    public void handleBatchCreated(EventEnvelope<Map<String, String>> event) {
        try {
            Map<String, String> payload = event.getPayload() != null ? event.getPayload() : Collections.emptyMap();
            String workspaceId = resolveWorkspaceId(event, payload);
            if (workspaceId == null) {
                return;
            }
            if (!registerEvent(event, AppConstants.TOPIC_BATCH_CREATED, workspaceId)) {
                return;
            }
            TenantContext.setTenantId(event.getTenantId());
            TenantContext.setWorkspaceId(workspaceId);
            String jobId = payload.get("jobId");
            String batchId = payload.get("batchId");
            
            // To pass payload we fetch from DB inside the service
            // This is a common pattern to avoid Kafka message size limits
            executionService.executeBatch(event.getTenantId(), jobId, batchId, null); // passing null as it will fetch from db

        } catch (Exception e) {
            log.error("Failed handling TOPIC_BATCH_CREATED {}", eventId(event), e);
            throw new IllegalStateException("Failed handling TOPIC_BATCH_CREATED", e);
        } finally {
            TenantContext.clear();
        }
    }

    @KafkaListener(topics = AppConstants.TOPIC_SEND_REQUESTED, groupId = AppConstants.GROUP_CAMPAIGN, concurrency = "2")
    public void handleAutomationSendRequest(EventEnvelope<String> event) {
        try {
            Map<String, Object> payload = event.getPayload() != null
                    ? objectMapper.readValue(event.getPayload(), new TypeReference<>() {})
                    : Collections.emptyMap();
            String campaignId = stringValue(payload.get("campaignId"));
            if (campaignId == null) {
                log.warn("Ignoring send.requested without campaignId: {}", event.getEventId());
                return;
            }
            String workspaceId = resolveWorkspaceId(event, payload);
            if (event.getWorkspaceId() == null && stringValue(payload.get("workspaceId")) == null) {
                workspaceId = campaignRepository.findByTenantIdAndIdAndDeletedAtIsNull(event.getTenantId(), campaignId)
                        .map(Campaign::getWorkspaceId)
                        .orElse(workspaceId);
            }
            if (workspaceId == null || workspaceId.isBlank()) {
                log.error("Dropping send.requested event without workspaceId. eventId={}, campaignId={}", event.getEventId(), campaignId);
                return;
            }
            if (!registerEvent(event, AppConstants.TOPIC_SEND_REQUESTED, workspaceId)) {
                return;
            }
            TenantContext.setTenantId(event.getTenantId());
            TenantContext.setWorkspaceId(workspaceId);

            CampaignDto.TriggerLaunchRequest request = CampaignDto.TriggerLaunchRequest.builder()
                    .triggerSource(stringValue(payload.getOrDefault("triggerSource", "AUTOMATION")))
                    .triggerReference(stringValue(payload.getOrDefault("triggerReference", payload.get("instanceId"))))
                    .idempotencyKey(stringValue(payload.getOrDefault("idempotencyKey", event.getIdempotencyKey())))
                    .build();
            orchestrationService.triggerFromAutomation(campaignId, request);
        } catch (Exception e) {
            log.error("Failed handling TOPIC_SEND_REQUESTED {}", eventId(event), e);
            throw new IllegalStateException("Failed handling TOPIC_SEND_REQUESTED", e);
        } finally {
            TenantContext.clear();
        }
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
        try {
            if (event.getPayload() == null || event.getPayload().isBlank()) {
                return;
            }
            Map<String, Object> payload = objectMapper.readValue(event.getPayload(), new TypeReference<>() {});
            String workspaceId = resolveWorkspaceId(event, payload);
            if (workspaceId == null) {
                return;
            }
            if (!registerEvent(event, AppConstants.TOPIC_TRACKING_INGESTED, workspaceId)) {
                return;
            }
            String campaignId = stringValue(payload.get("campaignId"));
            String experimentId = stringValue(payload.get("experimentId"));
            if (campaignId == null || experimentId == null) {
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
        } catch (Exception e) {
            log.error("Failed handling TOPIC_TRACKING_INGESTED {}", eventId(event), e);
            throw new IllegalStateException("Failed handling TOPIC_TRACKING_INGESTED", e);
        }
    }

    private void reconcileDeliveryFeedback(EventEnvelope<Map<String, Object>> event, boolean failed) {
        try {
            Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Collections.emptyMap();
            String workspaceId = resolveWorkspaceId(event, payload);
            if (workspaceId == null) {
                return;
            }
            String topic = failed ? AppConstants.TOPIC_EMAIL_FAILED : AppConstants.TOPIC_EMAIL_SENT;
            if (!registerEvent(event, topic, workspaceId)) {
                return;
            }
            TenantContext.setTenantId(event.getTenantId());
            TenantContext.setWorkspaceId(workspaceId);

            SendJob job = findSendJob(event.getTenantId(), workspaceId, payload);
            if (job == null) {
                log.warn("No send job found for delivery feedback event {}", event.getEventId());
                return;
            }

            String batchId = stringValue(payload.get("batchId"));
            if (batchId != null) {
                sendBatchRepository.findByTenantWorkspaceAndId(event.getTenantId(), workspaceId, batchId).ifPresent(batch -> {
                    batch.setProcessedCount((batch.getProcessedCount() == null ? 0 : batch.getProcessedCount()) + 1);
                    if (failed) {
                        batch.setFailureCount((batch.getFailureCount() == null ? 0 : batch.getFailureCount()) + 1);
                        batch.setLastError(stringValue(payload.get("reason")));
                    } else {
                        batch.setSuccessCount((batch.getSuccessCount() == null ? 0 : batch.getSuccessCount()) + 1);
                    }
                    if (batch.getProcessedCount() >= batch.getBatchSize()) {
                        if (batch.getFailureCount() != null && batch.getFailureCount() > 0) {
                            batch.setStatus(batch.getSuccessCount() != null && batch.getSuccessCount() > 0
                                    ? SendBatch.BatchStatus.PARTIAL : SendBatch.BatchStatus.FAILED);
                        } else {
                            batch.setStatus(SendBatch.BatchStatus.COMPLETED);
                        }
                    } else if (batch.getStatus() == SendBatch.BatchStatus.PENDING) {
                        batch.setStatus(SendBatch.BatchStatus.PROCESSING);
                    }
                    sendBatchRepository.save(batch);
                });
            }

            if (failed) {
                job.setTotalFailed((job.getTotalFailed() == null ? 0L : job.getTotalFailed()) + 1);
                String reason = stringValue(payload.get("reason"));
                if (reason != null) {
                    job.setErrorMessage(reason);
                }
            } else {
                job.setTotalSent((job.getTotalSent() == null ? 0L : job.getTotalSent()) + 1);
            }
            String messageId = stringValue(payload.get("messageId"));
            if (messageId != null) {
                sendSafetyService.recordDeliveryFeedback(
                        event.getTenantId(),
                        workspaceId,
                        messageId,
                        failed,
                        stringValue(payload.get("reason")));
            }
            sendJobRepository.save(job);

            reconcileJobAndCampaignState(job);
        } catch (Exception e) {
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

    private boolean registerEvent(EventEnvelope<?> event, String eventType, String workspaceId) {
        return idempotencyService.registerIfNew(
                event.getTenantId(),
                workspaceId,
                eventType,
                event.getEventId(),
                event.getIdempotencyKey()
        );
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
}

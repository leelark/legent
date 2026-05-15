package com.legent.campaign.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.campaign.client.ContentServiceClient;
import com.legent.campaign.domain.Campaign;
import com.legent.common.constant.AppConstants;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletionException;

import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendBatchRepository;
import com.legent.campaign.repository.SendJobRepository;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.kafka.producer.EventPublisher;
import com.legent.kafka.model.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for executing send batches.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableScheduling
public class SendExecutionService {

    private static final String EMPTY_BATCH_PAYLOAD = "[]";

    private final SendBatchRepository batchRepository;
    private final CampaignRepository campaignRepository;
    private final SendJobRepository sendJobRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ThrottlingService throttlingService;
    private final ContentServiceClient contentServiceClient;
    private final CampaignSendSafetyService sendSafetyService;
    private final CampaignStateMachineService stateMachine;

    @Value("${legent.campaign.send.max-batch-retries:5}")
    private int maxBatchRetries;

    @Value("${legent.campaign.send.render-cache-max-entries:1000}")
    private int maxRenderCacheEntries;

    @Value("${legent.campaign.send.max-email-payload-bytes:1048576}")
    private int maxEmailPayloadBytes;

    @Transactional
    public void executeBatch(String tenantId, String jobId, String batchId, String payloadJson) {
        try {
            SendBatch batch = findBatchWithRetry(tenantId, batchId);
            if (batch.getStatus() == SendBatch.BatchStatus.COMPLETED) return;
            if (batch.getStatus() == SendBatch.BatchStatus.PROCESSING) {
                log.info("Skipping batch {} because it is already processing", batchId);
                return;
            }
            if (batch.getCampaignId() == null || batch.getCampaignId().isBlank()) {
                throw new ValidationException("campaignId", "Batch campaignId is required");
            }

            batch.setStatus(SendBatch.BatchStatus.PROCESSING);
            batchRepository.saveAndFlush(batch);
            
            if (payloadJson == null || payloadJson.isEmpty()) {
                payloadJson = batch.getPayload();
            }

            List<Map<String, String>> subscribers = objectMapper.readValue(payloadJson, new TypeReference<>() {});
            subscribers = subscribers.stream()
                    .filter(sub -> sub != null && sub.get("email") != null && !sub.get("email").isBlank())
                    .toList();
            if (subscribers.isEmpty()) {
                batch.setStatus(SendBatch.BatchStatus.COMPLETED);
                batch.setPayload(EMPTY_BATCH_PAYLOAD);
                batchRepository.save(batch);
                reconcileJobIfFinished(batch);
                return;
            }
            
            // Fetch campaign to get template/content info
            Campaign campaign = campaignRepository.findByTenantIdAndIdAndDeletedAtIsNull(
                    tenantId, batch.getCampaignId())
                    .orElseThrow(() -> new NotFoundException("Campaign not found: " + batch.getCampaignId()));
            
            if (campaign.getContentId() == null || campaign.getContentId().isBlank()) {
                failBatch(tenantId, batch, "CAMPAIGN_CONTENT_REQUIRED", "Campaign must reference a published approved template before send");
                return;
            }
            
            CampaignSendSafetyService.SendPlan sendPlan = sendSafetyService.buildPlan(campaign);
            List<CampaignSendSafetyService.PreparedRecipient> preparedRecipients = new java.util.ArrayList<>();
            int skippedRecipients = 0;
            for (Map<String, String> sub : subscribers) {
                String subscriberIdentity = sub.get("subscriberId") != null ? sub.get("subscriberId") : sub.get("email");
                String messageId = batch.getJobId() + ":" + batchId + ":" + subscriberIdentity + ":r" + (batch.getRetryCount() == null ? 0 : batch.getRetryCount());
                CampaignSendSafetyService.PreparedRecipient prepared = sendSafetyService.prepareRecipient(
                        campaign,
                        batch,
                        sendPlan,
                        sub,
                        messageId);
                if (prepared.send()) {
                    preparedRecipients.add(prepared);
                } else {
                    skippedRecipients++;
                }
            }

            // Limit by domain throttling rules before publishing to delivery.
            int requestedPermits = throttlingService.acquirePermits(tenantId, batch.getDomain(), preparedRecipients.size());
            int acquiredPermits = Math.min(preparedRecipients.size(), Math.max(0, requestedPermits));
            int publishFailures = 0;
            int oversizedPayloads = 0;
            List<Map<String, String>> retrySubscribers = new java.util.ArrayList<>();
            Map<RenderCacheKey, ContentServiceClient.RenderedContent> renderCache = newRenderCache();
            
            for (int i = 0; i < acquiredPermits; i++) {
                CampaignSendSafetyService.PreparedRecipient prepared = preparedRecipients.get(i);
                Map<String, String> sub = prepared.subscriber();
                
                // Publish individual email send requests into Delivery Kafka Topic
                Map<String, Object> variables = new java.util.HashMap<>();
                variables.putAll(sub);
                variables.put("campaignId", batch.getCampaignId());
                variables.put("jobId", batch.getJobId());
                variables.put("batchId", batchId);
                if (prepared.experimentId() != null) {
                    variables.put("experimentId", prepared.experimentId());
                }
                if (prepared.variantId() != null) {
                    variables.put("variantId", prepared.variantId());
                }
                String renderContentId = prepared.contentIdOverride() != null && !prepared.contentIdOverride().isBlank()
                        ? prepared.contentIdOverride()
                        : campaign.getContentId();
                ContentServiceClient.RenderedContent rendered = renderWithBatchCache(
                        renderCache,
                        tenantId,
                        renderContentId,
                        prepared.subjectOverride(),
                        variables);
                String subject = prepared.subjectOverride() != null && !prepared.subjectOverride().isBlank()
                        ? prepared.subjectOverride()
                        : campaign.getSubject() != null && !campaign.getSubject().isBlank()
                        ? campaign.getSubject()
                        : rendered.subject();
                if (subject == null || subject.isBlank() || rendered.htmlBody() == null || rendered.htmlBody().isBlank()) {
                    throw new ValidationException("content", "Rendered email content is missing subject or HTML body");
                }

                Map<String, Object> emailPayload = new java.util.HashMap<>();
                emailPayload.put("email", sub.get("email"));
                emailPayload.put("subscriberId", sub.get("subscriberId"));
                emailPayload.put("campaignId", batch.getCampaignId());
                emailPayload.put("jobId", batch.getJobId());
                emailPayload.put("batchId", batchId);
                emailPayload.put("workspaceId", batch.getWorkspaceId());
                if (batch.getTeamId() != null) {
                    emailPayload.put("teamId", batch.getTeamId());
                }
                if (prepared.experimentId() != null) {
                    emailPayload.put("experimentId", prepared.experimentId());
                }
                if (prepared.variantId() != null) {
                    emailPayload.put("variantId", prepared.variantId());
                }
                emailPayload.put("holdout", false);
                emailPayload.put("costReserved", prepared.costReserved());
                emailPayload.put("subject", subject);
                emailPayload.put("htmlBody", rendered.htmlBody());
                if (rendered.textBody() != null && !rendered.textBody().isBlank()) {
                    emailPayload.put("textBody", rendered.textBody());
                }
                emailPayload.put("fromEmail", campaign.getSenderEmail());
                emailPayload.put("fromName", campaign.getSenderName());
                emailPayload.put("replyToEmail", campaign.getReplyToEmail());
                emailPayload.put("messageId", prepared.messageId());
                if (sub.get("firstName") != null) {
                    emailPayload.put("firstName", sub.get("firstName"));
                }
                if (sub.get("lastName") != null) {
                    emailPayload.put("lastName", sub.get("lastName"));
                }
                int payloadBytes = serializedPayloadBytes(emailPayload);
                if (payloadBytes > maxEmailPayloadBytes) {
                    String reason = "Rendered email payload " + payloadBytes + " bytes exceeds cap " + maxEmailPayloadBytes;
                    log.error("Rejecting oversized send request for message {}: {}", prepared.messageId(), reason);
                    sendSafetyService.recordDeliveryFeedback(
                            tenantId,
                            batch.getWorkspaceId(),
                            prepared.messageId(),
                            true,
                            "CONTENT_PAYLOAD_TOO_LARGE: " + reason);
                    publishFailures++;
                    oversizedPayloads++;
                    continue;
                }

                EventEnvelope<Map<String, Object>> envelope = EventEnvelope.wrap(
                        AppConstants.TOPIC_EMAIL_SEND_REQUESTED, tenantId, "campaign-service",
                        emailPayload
                );
                try {
                    publishAndAwait(AppConstants.TOPIC_EMAIL_SEND_REQUESTED, envelope);
                } catch (Exception e) {
                    log.error("Failed to publish send request", e);
                    sendSafetyService.recordDeliveryFeedback(
                            tenantId,
                            batch.getWorkspaceId(),
                            prepared.messageId(),
                            true,
                            e.getMessage());
                    publishFailures++;
                    retrySubscribers.add(sub);
                }
            }

            batch.setProcessedCount(batch.getProcessedCount() + skippedRecipients);
            if (skippedRecipients > 0) {
                final int skippedForJob = skippedRecipients;
                sendJobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                                tenantId, batch.getWorkspaceId(), batch.getJobId())
                        .ifPresent(job -> {
                            job.setTotalSuppressed((job.getTotalSuppressed() == null ? 0L : job.getTotalSuppressed()) + skippedForJob);
                            sendJobRepository.save(job);
                        });
            }
            
            // Delivery feedback owns success/failure recipient counters; handoff counters stay out of them.
            
            if (oversizedPayloads > 0) {
                batch.setStatus(SendBatch.BatchStatus.FAILED);
                batch.setPayload(EMPTY_BATCH_PAYLOAD);
                batch.setLastError("Rendered email payload exceeded configured cap");
                EventEnvelope<Map<String, Object>> failedEvent = EventEnvelope.wrap(
                        AppConstants.TOPIC_SEND_FAILED, tenantId, "campaign-service",
                        Map.of(
                                "batchId", batchId,
                                "jobId", batch.getJobId(),
                                "campaignId", batch.getCampaignId(),
                                "workspaceId", batch.getWorkspaceId(),
                                "reason", "CONTENT_PAYLOAD_TOO_LARGE",
                                "oversizedCount", oversizedPayloads
                        )
                );
                publishAndAwait(AppConstants.TOPIC_SEND_FAILED, failedEvent);
            } else if (publishFailures > 0 || acquiredPermits < preparedRecipients.size()) {
                batch.setStatus(SendBatch.BatchStatus.PARTIAL); // Requires retry later
                
                // Active DLQ / DL-Routing
                List<Map<String, String>> remaining = new java.util.ArrayList<>(retrySubscribers);
                remaining.addAll(preparedRecipients.subList(acquiredPermits, preparedRecipients.size())
                        .stream()
                        .map(CampaignSendSafetyService.PreparedRecipient::subscriber)
                        .toList());
                try {
                    String remainingPayload = objectMapper.writeValueAsString(remaining);
                    batch.setPayload(remainingPayload);
                    
                    // Route to DLQ or retry topic
                    EventEnvelope<Map<String, Object>> dlqEvent = EventEnvelope.wrap(
                            AppConstants.TOPIC_SEND_FAILED, tenantId, "campaign-service",
                            Map.of(
                                "batchId", batchId,
                                "jobId", batch.getJobId(),
                                "campaignId", batch.getCampaignId(),
                                "workspaceId", batch.getWorkspaceId(),
                                "reason", "RATE_LIMIT_EXCEEDED",
                                "unprocessedCount", remaining.size()
                            )
                    );
                    publishAndAwait(AppConstants.TOPIC_SEND_FAILED, dlqEvent);
                } catch (Exception se) {
                    log.error("Failed to process remaining subscribers for DLQ tracking on batch {}", batchId, se);
                    throw se;
                }
            } else {
                if (preparedRecipients.isEmpty()) {
                    batch.setStatus(SendBatch.BatchStatus.COMPLETED);
                } else if (publishFailures == acquiredPermits && acquiredPermits > 0) {
                    batch.setStatus(SendBatch.BatchStatus.FAILED);
                    batch.setLastError("All rendered send requests failed");
                } else if (publishFailures > 0) {
                    batch.setStatus(SendBatch.BatchStatus.PARTIAL);
                    batch.setLastError("Some rendered send requests failed");
                } else {
                    batch.setStatus(SendBatch.BatchStatus.COMPLETED);
                }
            }
            if (batch.getStatus() == SendBatch.BatchStatus.COMPLETED) {
                batch.setPayload(EMPTY_BATCH_PAYLOAD);
            }
            batchRepository.save(batch);
            reconcileJobIfFinished(batch);
        } catch (Exception e) {
            log.error("Failed to enqueue batch {}", batchId, e);
            throw new IllegalStateException("Failed to execute send batch " + batchId, e);
        }
    }

    private <T> void publishAndAwait(String topic, EventEnvelope<T> envelope) {
        try {
            eventPublisher.publish(topic, envelope).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to publish event to " + topic, cause);
        }
    }

    private Map<RenderCacheKey, ContentServiceClient.RenderedContent> newRenderCache() {
        int maxEntries = Math.max(0, maxRenderCacheEntries);
        if (maxEntries == 0) {
            return Collections.emptyMap();
        }
        return new LinkedHashMap<>(Math.min(16, maxEntries), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<RenderCacheKey, ContentServiceClient.RenderedContent> eldest) {
                return size() > maxEntries;
            }
        };
    }

    private int serializedPayloadBytes(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsBytes(payload).length;
        } catch (JsonProcessingException e) {
            throw new ValidationException("payload", "Unable to serialize send request payload");
        }
    }

    private ContentServiceClient.RenderedContent renderWithBatchCache(
            Map<RenderCacheKey, ContentServiceClient.RenderedContent> renderCache,
            String tenantId,
            String renderContentId,
            String subjectOverride,
            Map<String, Object> variables) {
        if (renderCache.isEmpty() && maxRenderCacheEntries <= 0) {
            return contentServiceClient.renderTemplate(tenantId, renderContentId, variables);
        }
        RenderCacheKey key = RenderCacheKey.from(renderContentId, subjectOverride, variables);
        ContentServiceClient.RenderedContent cached = renderCache.get(key);
        if (cached != null) {
            return cached;
        }
        ContentServiceClient.RenderedContent rendered = contentServiceClient.renderTemplate(tenantId, renderContentId, variables);
        renderCache.put(key, rendered);
        return rendered;
    }

    private record RenderCacheKey(String renderContentId,
                                  String subjectOverride,
                                  SortedMap<String, String> variables) {
        static RenderCacheKey from(String renderContentId, String subjectOverride, Map<String, Object> variables) {
            TreeMap<String, String> normalizedVariables = new TreeMap<>();
            if (variables != null) {
                variables.forEach((key, value) -> {
                    if (key != null) {
                        normalizedVariables.put(key, value == null ? null : value.toString());
                    }
                });
            }
            return new RenderCacheKey(
                    normalizeText(renderContentId),
                    normalizeText(subjectOverride),
                    Collections.unmodifiableSortedMap(normalizedVariables));
        }

        private static String normalizeText(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.trim();
            return normalized.isEmpty() ? null : normalized;
        }
    }

    private void failBatch(String tenantId, SendBatch batch, String reason, String message) {
        batch.setStatus(SendBatch.BatchStatus.FAILED);
        batch.setLastError(message);
        batch.setFailureCount(batch.getBatchSize());
        batchRepository.save(batch);
        EventEnvelope<Map<String, Object>> event = EventEnvelope.wrap(
                AppConstants.TOPIC_SEND_FAILED,
                tenantId,
                "campaign-service",
                Map.of(
                        "batchId", batch.getId(),
                        "jobId", batch.getJobId(),
                        "campaignId", batch.getCampaignId(),
                        "workspaceId", batch.getWorkspaceId(),
                        "reason", reason,
                        "message", message
                )
        );
        publishAndAwait(AppConstants.TOPIC_SEND_FAILED, event);
        reconcileJobIfFinished(batch);
    }

    private SendBatch findBatchWithRetry(String tenantId, String batchId) {
        // BatchingService uses TransactionSynchronization.afterCommit() to ensure
        // batch is persisted before publishing batch.created event.
        // No polling needed - batch should exist when this method is called.
        String workspaceId = com.legent.security.TenantContext.getWorkspaceId();
        if (workspaceId != null && !workspaceId.isBlank()) {
            return batchRepository.findByTenantWorkspaceAndId(tenantId, workspaceId, batchId)
                    .orElseThrow(() -> new com.legent.common.exception.NotFoundException("SendBatch", batchId));
        }
        return batchRepository.findById(batchId)
                .filter(batch -> tenantId.equals(batch.getTenantId()))
                .orElseThrow(() -> new com.legent.common.exception.NotFoundException("SendBatch", batchId));
    }

    /**
     * Scheduled job to retry PARTIAL batches.
     * Runs every 5 minutes to requeue partial batches for processing.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 300000)
    @Transactional
    public void retryPartialBatches() {
        List<SendBatch> partialBatches = batchRepository.findByStatus(SendBatch.BatchStatus.PARTIAL);
        log.info("Found {} PARTIAL batches to retry", partialBatches.size());

        for (SendBatch batch : partialBatches) {
            try {
                int nextRetryCount = batch.getRetryCount() != null ? batch.getRetryCount() + 1 : 1;
                if (nextRetryCount > maxBatchRetries) {
                    batch.setStatus(SendBatch.BatchStatus.FAILED);
                    batch.setLastError("Max campaign batch retries exceeded");
                    batch.setRetryCount(nextRetryCount);
                    batchRepository.save(batch);
                    sendSafetyService.createDeadLetter(
                            batch.getTenantId(),
                            batch.getWorkspaceId(),
                            batch.getCampaignId(),
                            batch.getJobId(),
                            batch.getId(),
                            "MAX_BATCH_RETRIES_EXCEEDED",
                            batch.getPayload(),
                            nextRetryCount);
                    reconcileJobIfFinished(batch);
                    continue;
                }
                // Reset status to PENDING so it can be picked up again
                batch.setStatus(SendBatch.BatchStatus.PENDING);
                batch.setRetryCount(nextRetryCount);
                batchRepository.save(batch);

                // Publish batch created event to trigger reprocessing
                EventEnvelope<Map<String, Object>> retryEvent = EventEnvelope.wrap(
                        AppConstants.TOPIC_BATCH_CREATED, batch.getTenantId(), "campaign-service",
                        Map.of(
                            "batchId", batch.getId(),
                            "jobId", batch.getJobId(),
                            "workspaceId", batch.getWorkspaceId(),
                            "campaignId", batch.getCampaignId(),
                            "retry", true,
                            "retryCount", batch.getRetryCount()
                        )
                );
                publishAndAwait(AppConstants.TOPIC_BATCH_CREATED, retryEvent);
                log.info("Requeued PARTIAL batch {} for retry (attempt {})", batch.getId(), batch.getRetryCount());
            } catch (Exception e) {
                log.error("Failed to requeue PARTIAL batch {} for retry", batch.getId(), e);
            }
        }
    }

    private void reconcileJobIfFinished(SendBatch batch) {
        long totalBatches = batchRepository.countByTenantWorkspaceAndJob(batch.getTenantId(), batch.getWorkspaceId(), batch.getJobId());
        if (totalBatches == 0) {
            return;
        }
        long activeBatches = batchRepository.countByTenantWorkspaceAndJobAndStatuses(
                batch.getTenantId(),
                batch.getWorkspaceId(),
                batch.getJobId(),
                List.of(SendBatch.BatchStatus.PENDING, SendBatch.BatchStatus.PROCESSING, SendBatch.BatchStatus.PARTIAL));
        if (activeBatches > 0) {
            return;
        }
        sendJobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                batch.getTenantId(), batch.getWorkspaceId(), batch.getJobId()).ifPresent(job -> {
            if (job.getStatus() == SendJob.JobStatus.COMPLETED
                    || job.getStatus() == SendJob.JobStatus.FAILED
                    || job.getStatus() == SendJob.JobStatus.CANCELLED) {
                return;
            }
            long failedBatches = batchRepository.countByTenantWorkspaceAndJobAndStatuses(
                    batch.getTenantId(),
                    batch.getWorkspaceId(),
                    batch.getJobId(),
                    List.of(SendBatch.BatchStatus.FAILED));
            if (failedBatches > 0) {
                stateMachine.transitionJob(job, SendJob.JobStatus.FAILED, "One or more send batches failed");
            } else {
                stateMachine.transitionJob(job, SendJob.JobStatus.COMPLETED, "All batches processed");
            }
            sendJobRepository.save(job);

            campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                    job.getTenantId(), job.getWorkspaceId(), job.getCampaignId()).ifPresent(campaign -> {
                if (campaign.getStatus() == Campaign.CampaignStatus.CANCELLED
                        || campaign.getStatus() == Campaign.CampaignStatus.ARCHIVED
                        || campaign.getStatus() == Campaign.CampaignStatus.COMPLETED
                        || campaign.getStatus() == Campaign.CampaignStatus.FAILED) {
                    return;
                }
                if (job.getStatus() == SendJob.JobStatus.FAILED) {
                    stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.FAILED, job.getErrorMessage());
                } else {
                    stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.COMPLETED, "Campaign send completed");
                }
                campaignRepository.save(campaign);
            });
        });
    }
}

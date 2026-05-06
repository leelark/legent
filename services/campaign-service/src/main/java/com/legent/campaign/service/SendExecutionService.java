package com.legent.campaign.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.campaign.client.ContentServiceClient;
import com.legent.campaign.domain.Campaign;
import com.legent.common.constant.AppConstants;
import java.util.List;

import java.util.Map;

import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendBatchRepository;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.kafka.producer.EventPublisher;
import com.legent.kafka.model.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final SendBatchRepository batchRepository;
    private final CampaignRepository campaignRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ThrottlingService throttlingService;
    private final ContentServiceClient contentServiceClient;

    @Transactional
    public void executeBatch(String tenantId, String jobId, String batchId, String payloadJson) {
        try {
            SendBatch batch = findBatchWithRetry(tenantId, batchId);
            if (batch.getStatus() == SendBatch.BatchStatus.COMPLETED) return;
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
                batchRepository.save(batch);
                return;
            }
            
            // Fetch campaign to get template/content info
            Campaign campaign = campaignRepository.findByTenantIdAndIdAndDeletedAtIsNull(
                    tenantId, batch.getCampaignId())
                    .orElseThrow(() -> new NotFoundException("Campaign not found: " + batch.getCampaignId()));
            
            // Fetch and render template content
            String subject = campaign.getSubject();
            String htmlBody = null;
            
            if (campaign.getContentId() != null && !campaign.getContentId().isBlank()) {
                ContentServiceClient.TemplateVersionDto version = contentServiceClient.getLatestVersion(
                        tenantId, campaign.getContentId());
                if (version != null) {
                    if (subject == null || subject.isBlank()) {
                        subject = version.subject();
                    }
                    htmlBody = version.htmlContent();
                }
            }
            
            // Fallback if no template found
            if (subject == null || subject.isBlank()) {
                subject = "Legent Campaign";
            }
            if (htmlBody == null || htmlBody.isBlank()) {
                htmlBody = "<html><body>Email content</body></html>";
            }
            
            // Limit by domain throttling rules before publishing to delivery
            int acquiredPermits = throttlingService.acquirePermits(tenantId, batch.getDomain(), subscribers.size());
            
            for (int i = 0; i < acquiredPermits; i++) {
                Map<String, String> sub = subscribers.get(i);
                
                // Publish individual email send requests into Delivery Kafka Topic
                try {
                    Map<String, Object> emailPayload = new java.util.HashMap<>();
                    emailPayload.put("email", sub.get("email"));
                    emailPayload.put("subscriberId", sub.get("subscriberId"));
                    emailPayload.put("campaignId", batch.getCampaignId());
                    emailPayload.put("jobId", batch.getJobId());
                    emailPayload.put("batchId", batchId);
                    emailPayload.put("workspaceId", batch.getWorkspaceId());
                    emailPayload.put("subject", subject);
                    emailPayload.put("htmlBody", htmlBody);
                    String subscriberIdentity = sub.get("subscriberId") != null ? sub.get("subscriberId") : sub.get("email");
                    if (subscriberIdentity != null) {
                        emailPayload.put("messageId", batch.getJobId() + ":" + batchId + ":" + subscriberIdentity);
                    }
                    if (sub.get("firstName") != null) {
                        emailPayload.put("firstName", sub.get("firstName"));
                    }
                    if (sub.get("lastName") != null) {
                        emailPayload.put("lastName", sub.get("lastName"));
                    }
                    
                    EventEnvelope<Map<String, Object>> envelope = EventEnvelope.wrap(
                            AppConstants.TOPIC_EMAIL_SEND_REQUESTED, tenantId, "campaign-service",
                            emailPayload
                    );
                    eventPublisher.publish(AppConstants.TOPIC_EMAIL_SEND_REQUESTED, envelope);
                } catch (Exception e) {
                    log.error("Failed to publish send request", e);
                }
            }

            batch.setProcessedCount(batch.getProcessedCount() + acquiredPermits);
            
            // Note: success and failure count updates will now be managed via tracking webhook later.
            // For now, we consider them successful handoffs to the delivery engine queue.
            batch.setSuccessCount(batch.getSuccessCount() + acquiredPermits);
            
            if (acquiredPermits < subscribers.size()) {
                batch.setStatus(SendBatch.BatchStatus.PARTIAL); // Requires retry later
                
                // Active DLQ / DL-Routing
                List<Map<String, String>> remaining = subscribers.subList(acquiredPermits, subscribers.size());
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
                    eventPublisher.publish(AppConstants.TOPIC_SEND_FAILED, dlqEvent);
                } catch (Exception se) {
                    log.error("Failed to process remaining subscribers for DLQ tracking on batch {}", batchId, se);
                }
            } else {
                batch.setStatus(SendBatch.BatchStatus.COMPLETED);
            }
            batchRepository.save(batch);
        } catch (Exception e) {
            log.error("Failed to enqueue batch {}", batchId, e);
        }
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
                // Reset status to PENDING so it can be picked up again
                batch.setStatus(SendBatch.BatchStatus.PENDING);
                batch.setRetryCount(batch.getRetryCount() != null ? batch.getRetryCount() + 1 : 1);
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
                eventPublisher.publish(AppConstants.TOPIC_BATCH_CREATED, retryEvent);
                log.info("Requeued PARTIAL batch {} for retry (attempt {})", batch.getId(), batch.getRetryCount());
            } catch (Exception e) {
                log.error("Failed to requeue PARTIAL batch {} for retry", batch.getId(), e);
            }
        }
    }
}

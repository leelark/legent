package com.legent.campaign.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import java.util.List;

import java.util.Map;

import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.repository.SendBatchRepository;
import com.legent.common.exception.ValidationException;
import com.legent.kafka.producer.EventPublisher;
import com.legent.kafka.model.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor

public class SendExecutionService {

    private final SendBatchRepository batchRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ThrottlingService throttlingService;

    @Transactional
    public void executeBatch(String tenantId, String jobId, String batchId, String payloadJson) {
        try {
            SendBatch batch = findBatchWithRetry(batchId);
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
            
            // Limit by domain throttling rules before publishing to delivery
            int acquiredPermits = throttlingService.acquirePermits(tenantId, batch.getDomain(), subscribers.size());
            
            for (int i = 0; i < acquiredPermits; i++) {
                Map<String, String> sub = subscribers.get(i);
                
                // Publish individual email send requests into Delivery Kafka Topic
                try {
                    EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                            AppConstants.TOPIC_EMAIL_SEND_REQUESTED, tenantId, "campaign-service",
                            Map.of(
                                    "email", sub.get("email"),
                                    "subscriberId", sub.get("subscriberId"),
                                    "campaignId", batch.getCampaignId(),
                                    "batchId", batchId
                            )
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
                                "campaignId", batch.getCampaignId(),
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

    private SendBatch findBatchWithRetry(String batchId) {
        for (int attempt = 1; attempt <= 5; attempt++) {
            java.util.Optional<SendBatch> batch = batchRepository.findById(batchId);
            if (batch.isPresent()) {
                return batch.get();
            }

            if (attempt < 5) {
                try {
                    Thread.sleep(150L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for batch", interruptedException);
                }
            }
        }

        throw new RuntimeException("Batch " + batchId + " not found after 5 retries");
    }
}

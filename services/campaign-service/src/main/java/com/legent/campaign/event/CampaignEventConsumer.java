package com.legent.campaign.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import java.util.List;

import java.util.Map;

import com.legent.campaign.service.BatchingService;
import com.legent.campaign.service.SendExecutionService;
import com.legent.kafka.model.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignEventConsumer {

    private final BatchingService batchingService;
    private final SendExecutionService executionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = AppConstants.TOPIC_AUDIENCE_RESOLVED, groupId = AppConstants.GROUP_CAMPAIGN)
    public void handleAudienceResolved(EventEnvelope<Map<String, Object>> event) {
        try {
            Map<String, Object> payload = event.getPayload();
            String jobId = (String) payload.get("jobId");
            boolean isLastChunk = (Boolean) payload.getOrDefault("isLastChunk", true);
            
            // Re-serialize and deserialize to get typed list
            List<Map<String, String>> subscribers = objectMapper.convertValue(
                    payload.get("subscribers"), new TypeReference<>() {}
            );

            log.info("Received resolved audience chunk for job {}. Size: {}, isLast: {}", jobId, subscribers.size(), isLastChunk);
            batchingService.processResolvedAudienceChunk(event.getTenantId(), jobId, subscribers, isLastChunk);

        } catch (Exception e) {
            log.error("Failed handling TOPIC_AUDIENCE_RESOLVED {}", event.getEventId(), e);
        }
    }

    @KafkaListener(topics = AppConstants.TOPIC_SEND_PROCESSING, groupId = AppConstants.GROUP_CAMPAIGN, concurrency = "3")
    public void handleSendProcessing(EventEnvelope<Map<String, Object>> event) {
        try {
            Map<String, Object> payload = event.getPayload();
            String jobId = String.valueOf(payload.getOrDefault("jobId", ""));
            String batchId = String.valueOf(payload.getOrDefault("batchId", ""));
            if (!jobId.isBlank() && !batchId.isBlank()) {
                executionService.executeBatch(event.getTenantId(), jobId, batchId, null);
            } else {
                log.warn("SEND_PROCESSING event {} missing jobId/batchId — ignored", event.getEventId());
            }
        } catch (Exception e) {
            log.error("Failed handling TOPIC_SEND_PROCESSING {}", event.getEventId(), e);
        }
    }

    @KafkaListener(topics = AppConstants.TOPIC_BATCH_CREATED, groupId = AppConstants.GROUP_CAMPAIGN, concurrency = "3")
    public void handleBatchCreated(EventEnvelope<Map<String, String>> event) {
        try {
            Map<String, String> payload = event.getPayload();
            String jobId = payload.get("jobId");
            String batchId = payload.get("batchId");
            
            // To pass payload we fetch from DB inside the service
            // This is a common pattern to avoid Kafka message size limits
            executionService.executeBatch(event.getTenantId(), jobId, batchId, null); // passing null as it will fetch from db

        } catch (Exception e) {
            log.error("Failed handling TOPIC_BATCH_CREATED {}", event.getEventId(), e);
        }
    }
}

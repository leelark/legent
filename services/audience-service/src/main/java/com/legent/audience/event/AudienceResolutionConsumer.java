package com.legent.audience.event;

import com.legent.common.constant.AppConstants;
import java.util.ArrayList;
import java.util.List;

import java.util.Map;

import com.legent.audience.domain.Subscriber;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class AudienceResolutionConsumer {

    private final EventPublisher eventPublisher;
    private final SubscriberRepository subscriberRepository;

    @KafkaListener(topics = AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED, groupId = AppConstants.GROUP_AUDIENCE)
    public void handleResolutionRequest(EventEnvelope<Map<String, String>> event) {
        String tenantId = event.getTenantId();
        String campaignId = event.getPayload().get("campaignId");
        String jobId = event.getPayload().get("jobId");

        log.info("Received audience resolution request for job {}", jobId);
        
        try {
            TenantContext.setTenantId(tenantId);
            
            // In a real implementation, we would query the specific segments/lists for this campaign.
            // For this mock, we just send all active subscribers in 1 chunk.
            List<Subscriber> audience = subscriberRepository.findAll();
            
            List<Map<String, String>> chunk = new ArrayList<>();
            for (Subscriber sub : audience) {
                chunk.add(Map.of(
                    "subscriberId", sub.getId(),
                    "email", sub.getEmail(),
                    "subscriberKey", sub.getSubscriberKey()
                ));
            }
            
            EventEnvelope<Map<String, Object>> responseEvent = EventEnvelope.wrap(
                AppConstants.TOPIC_AUDIENCE_RESOLVED, tenantId, "audience-service",
                Map.of(
                    "campaignId", campaignId,
                    "jobId", jobId,
                    "isLastChunk", true,
                    "subscribers", chunk
                )
            );
            
            eventPublisher.publish(AppConstants.TOPIC_AUDIENCE_RESOLVED, responseEvent);
            log.info("Sent resolved chunk with {} subscribers for job {}", chunk.size(), jobId);
            
        } catch (Exception e) {
            log.error("Failed to resolve audience for job {}", jobId, e);
        } finally {
            TenantContext.clear();
        }
    }
}

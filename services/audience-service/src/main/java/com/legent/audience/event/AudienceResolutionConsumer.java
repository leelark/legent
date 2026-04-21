package com.legent.audience.event;

import com.legent.common.constant.AppConstants;
import java.util.ArrayList;
import java.util.List;

import java.util.Map;

import com.legent.audience.domain.Subscriber;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.audience.service.SegmentEvaluationService;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;


@Slf4j
@Component
@RequiredArgsConstructor
public class AudienceResolutionConsumer {

    private final EventPublisher eventPublisher;
    private final SubscriberRepository subscriberRepository;
    private final SegmentEvaluationService segmentEvaluationService;

    @KafkaListener(topics = AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED, groupId = AppConstants.GROUP_AUDIENCE)
    public void handleResolutionRequest(EventEnvelope<Map<String, Object>> event) {
        String tenantId = event.getTenantId();
        String campaignId = (String) event.getPayload().get("campaignId");
        String jobId = (String) event.getPayload().get("jobId");

        log.info("Received audience resolution request for job {}", jobId);
        
        try {
            TenantContext.setTenantId(tenantId);
            
            @SuppressWarnings("unchecked")
            List<Map<String, String>> audiences = (List<Map<String, String>>) event.getPayload().get("audiences");
            Set<String> subscriberIds = new HashSet<>();

            if (audiences == null || audiences.isEmpty()) {
                // Default: All subscribers if no audience specified
                subscriberIds.addAll(subscriberRepository.findAll().stream()
                        .map(Subscriber::getId).collect(Collectors.toSet()));
            } else {
                for (Map<String, String> aud : audiences) {
                    String type = aud.get("type");
                    String id = aud.get("id");
                    if ("SEGMENT".equalsIgnoreCase(type)) {
                        subscriberIds.addAll(segmentEvaluationService.getSegmentMembers(id));
                    } else if ("LIST".equalsIgnoreCase(type)) {
                        // Assuming list logic here, or just all for now
                        // In a real app, lists would have a separate table or segment-like logic
                    }
                }
            }

            List<Subscriber> audience = subscriberRepository.findAllById(subscriberIds);
            
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

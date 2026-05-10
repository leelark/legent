package com.legent.audience.event;

import com.legent.audience.client.DeliverabilityServiceClient;
import com.legent.audience.domain.Subscriber;
import com.legent.audience.repository.ListMembershipRepository;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.audience.service.AudienceEventIdempotencyService;
import com.legent.audience.service.SegmentEvaluationService;
import com.legent.audience.service.SendEligibilityService;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Component
@RequiredArgsConstructor
public class AudienceResolutionConsumer {

    private final EventPublisher eventPublisher;
    private final SubscriberRepository subscriberRepository;
    private final ListMembershipRepository listMembershipRepository;
    private final SegmentEvaluationService segmentEvaluationService;
    private final DeliverabilityServiceClient deliverabilityClient;
    private final AudienceEventIdempotencyService idempotencyService;
    private final SendEligibilityService sendEligibilityService;

    @KafkaListener(topics = AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED, groupId = AppConstants.GROUP_AUDIENCE)
    public void handleResolutionRequest(EventEnvelope<Map<String, Object>> event) {
        String tenantId = event.getTenantId();
        String workspaceId = event.getWorkspaceId();
        if (workspaceId == null || workspaceId.isBlank()) {
            log.error("Dropping audience resolution request without workspaceId. eventId={}", event.getEventId());
            return;
        }
        String campaignId = (String) event.getPayload().get("campaignId");
        String jobId = (String) event.getPayload().get("jobId");

        log.info("Received audience resolution request for job {}", jobId);
        
        try {
            TenantContext.setTenantId(tenantId);
            TenantContext.setWorkspaceId(workspaceId);

            boolean newEvent = idempotencyService.registerIfNew(
                    tenantId,
                    workspaceId,
                    String.valueOf(event.getEventType()),
                    event.getEventId(),
                    event.getIdempotencyKey());
            if (!newEvent) {
                return;
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, String>> audiences = (List<Map<String, String>>) event.getPayload().get("audiences");
            log.debug("Audience resolution payload: tenant={}, workspace={}, campaign={}, audiences={}",
                    tenantId, workspaceId, campaignId, audiences);
            Set<String> includedSubscriberIds = new HashSet<>();
            Set<String> excludedSubscriberIds = new HashSet<>();
            boolean hasIncludeRule = false;

            if (audiences == null || audiences.isEmpty()) {
                includedSubscriberIds.addAll(subscriberRepository.findIdsByTenantIdAndWorkspaceIdAndDeletedAtIsNull(tenantId, workspaceId));
                hasIncludeRule = true;
            } else {
                for (Map<String, String> aud : audiences) {
                    String type = aud.get("type");
                    String id = aud.get("id");
                    String action = aud.getOrDefault("action", "INCLUDE");

                    if (id == null || id.isBlank()) {
                        continue;
                    }

                    Set<String> resolvedIds = new HashSet<>();
                    if ("SEGMENT".equalsIgnoreCase(type)) {
                        resolvedIds.addAll(segmentEvaluationService.getSegmentMembers(id));
                    } else if ("LIST".equalsIgnoreCase(type)) {
                        resolvedIds.addAll(listMembershipRepository.findActiveSubscriberIdsByTenantAndWorkspaceAndListId(tenantId, workspaceId, id));
                    } else {
                        log.warn("Unsupported audience type '{}' for audience id '{}'", type, id);
                    }
                    log.debug("Resolved audience type={}, id={}, action={} -> {} subscribers", type, id, action, resolvedIds.size());

                    if ("EXCLUDE".equalsIgnoreCase(action)) {
                        excludedSubscriberIds.addAll(resolvedIds);
                    } else {
                        hasIncludeRule = true;
                        includedSubscriberIds.addAll(resolvedIds);
                    }
                }
            }

            if (!hasIncludeRule && !excludedSubscriberIds.isEmpty()) {
                includedSubscriberIds.addAll(subscriberRepository.findIdsByTenantIdAndWorkspaceIdAndDeletedAtIsNull(tenantId, workspaceId));
            }
            includedSubscriberIds.removeAll(excludedSubscriberIds);

            List<Subscriber> audience = includedSubscriberIds.isEmpty()
                    ? List.of()
                    : subscriberRepository.findByTenantIdAndWorkspaceIdAndIdInAndDeletedAtIsNull(tenantId, workspaceId, new ArrayList<>(includedSubscriberIds));

            // Check for suppressed subscribers (bounced, complained, unsubscribed)
            List<String> emailsToCheck = audience.stream()
                    .map(Subscriber::getEmail)
                    .distinct()
                    .collect(Collectors.toList());

            Set<String> suppressedEmails = deliverabilityClient.checkSuppressedEmails(tenantId, workspaceId, emailsToCheck);

            // Filter out suppressed subscribers
            List<Subscriber> filteredAudience = audience.stream()
                    .filter(sub -> !suppressedEmails.contains(sub.getEmail()))
                    .filter(sendEligibilityService::isSendEligible)
                    .collect(Collectors.toList());

            int suppressedCount = audience.size() - filteredAudience.size();
            if (suppressedCount > 0) {
                log.info("Filtered out {} suppressed subscribers for job {}", suppressedCount, jobId);
            }

            List<Map<String, String>> chunk = new ArrayList<>();
            for (Subscriber sub : filteredAudience) {
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

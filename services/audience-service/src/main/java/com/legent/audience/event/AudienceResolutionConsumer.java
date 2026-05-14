package com.legent.audience.event;

import com.legent.audience.client.DeliverabilityServiceClient;
import com.legent.audience.domain.Subscriber;
import com.legent.audience.repository.AudienceCandidateRepository;
import com.legent.audience.repository.AudienceCandidateRepository.AudienceCandidateCriteria;
import com.legent.audience.service.AudienceEventIdempotencyService;
import com.legent.audience.service.SendEligibilityService;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;


@Slf4j
@Component
@RequiredArgsConstructor
public class AudienceResolutionConsumer {

    private static final int RESOLVED_AUDIENCE_CHUNK_SIZE = AppConstants.SEND_BATCH_SIZE;
    private static final int UNKNOWN_TOTAL = -1;

    private final EventPublisher eventPublisher;
    private final AudienceCandidateRepository audienceCandidateRepository;
    private final DeliverabilityServiceClient deliverabilityClient;
    private final AudienceEventIdempotencyService idempotencyService;
    private final SendEligibilityService sendEligibilityService;

    @Transactional
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
            Set<String> includeListIds = new HashSet<>();
            Set<String> includeSegmentIds = new HashSet<>();
            Set<String> excludeListIds = new HashSet<>();
            Set<String> excludeSegmentIds = new HashSet<>();
            boolean hasIncludeRule = false;
            boolean includeAllSubscribers = false;

            if (audiences == null || audiences.isEmpty()) {
                includeAllSubscribers = true;
            } else {
                for (Map<String, String> aud : audiences) {
                    String type = aud.get("type");
                    String id = aud.get("id");
                    String action = aud.getOrDefault("action", "INCLUDE");

                    if (id == null || id.isBlank()) {
                        continue;
                    }

                    boolean exclude = "EXCLUDE".equalsIgnoreCase(action);
                    if ("SEGMENT".equalsIgnoreCase(type)) {
                        if (exclude) {
                            excludeSegmentIds.add(id);
                        } else {
                            hasIncludeRule = true;
                            includeSegmentIds.add(id);
                        }
                    } else if ("LIST".equalsIgnoreCase(type)) {
                        if (exclude) {
                            excludeListIds.add(id);
                        } else {
                            hasIncludeRule = true;
                            includeListIds.add(id);
                        }
                    } else {
                        log.warn("Unsupported audience type '{}' for audience id '{}'", type, id);
                    }
                }
            }

            if (!hasIncludeRule && (!excludeListIds.isEmpty() || !excludeSegmentIds.isEmpty())) {
                includeAllSubscribers = true;
            }

            AudienceCandidateCriteria criteria = new AudienceCandidateCriteria(
                    includeAllSubscribers,
                    includeListIds,
                    includeSegmentIds,
                    excludeListIds,
                    excludeSegmentIds);

            PublishingResult result = publishResolvedAudienceChunks(tenantId, workspaceId, campaignId, jobId, criteria);
            if (result.filteredCount() > 0) {
                log.info("Filtered out {} suppressed or ineligible subscribers for job {}", result.filteredCount(), jobId);
            }
            
        } catch (Exception e) {
            log.error("Failed to resolve audience for job {}", jobId, e);
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to resolve audience for job " + jobId, e);
        } finally {
            TenantContext.clear();
        }
    }

    private PublishingResult publishResolvedAudienceChunks(
            String tenantId,
            String workspaceId,
            String campaignId,
            String jobId,
            AudienceCandidateCriteria criteria) {
        PublishingState state = new PublishingState();
        forEachCandidatePage(tenantId, workspaceId, criteria, pageSubscribers -> {
            List<Subscriber> eligibleSubscribers = filterEligibleSubscribers(tenantId, workspaceId, pageSubscribers);
            state.addFiltered(pageSubscribers.size() - eligibleSubscribers.size());
            for (Subscriber subscriber : eligibleSubscribers) {
                state.currentChunk().add(toResolvedSubscriberPayload(subscriber));
                state.incrementTotalSubscribers();
                if (state.currentChunk().size() == RESOLVED_AUDIENCE_CHUNK_SIZE) {
                    queueResolvedAudienceChunk(
                            tenantId,
                            campaignId,
                            jobId,
                            state,
                            state.drainChunk());
                }
            }
        });

        if (!state.currentChunk().isEmpty()) {
            queueResolvedAudienceChunk(
                    tenantId,
                    campaignId,
                    jobId,
                    state,
                    state.drainChunk());
        }

        if (state.pendingChunk() == null) {
            publishResolvedAudienceChunk(tenantId, campaignId, jobId, List.of(), 0, 1, 0, true);
        } else {
            PendingChunk finalChunk = state.pendingChunk();
            publishResolvedAudienceChunk(
                    tenantId,
                    campaignId,
                    jobId,
                    finalChunk.subscribers(),
                    finalChunk.chunkIndex(),
                    state.totalChunks(),
                    state.totalSubscribers(),
                    true);
        }
        return new PublishingResult(state.totalSubscribers(), state.filteredCount());
    }

    private void queueResolvedAudienceChunk(
            String tenantId,
            String campaignId,
            String jobId,
            PublishingState state,
            List<Map<String, String>> chunk) {
        PendingChunk previous = state.replacePendingChunk(new PendingChunk(state.nextChunkIndex(), chunk));
        if (previous != null) {
            publishResolvedAudienceChunk(
                    tenantId,
                    campaignId,
                    jobId,
                    previous.subscribers(),
                    previous.chunkIndex(),
                    UNKNOWN_TOTAL,
                    UNKNOWN_TOTAL,
                    false);
        }
    }

    private void publishResolvedAudienceChunk(
            String tenantId,
            String campaignId,
            String jobId,
            List<Map<String, String>> chunk,
            int chunkIndex,
            int totalChunks,
            int totalSubscribers,
            boolean isLastChunk) {
        EventEnvelope<Map<String, Object>> responseEvent = EventEnvelope.wrap(
            AppConstants.TOPIC_AUDIENCE_RESOLVED, tenantId, "audience-service",
            Map.of(
                "campaignId", campaignId,
                "jobId", jobId,
                "chunkId", resolvedChunkId(jobId, chunkIndex),
                "chunkIndex", chunkIndex,
                "totalChunks", totalChunks,
                "chunkSize", chunk.size(),
                "totalResolvedSubscribers", totalSubscribers,
                "isLastChunk", isLastChunk,
                "subscribers", chunk
            )
        );

        publishResolvedAudienceEvent(tenantId, jobId, responseEvent);
        log.info("Sent resolved audience chunk {}/{} with {} subscribers for job {}",
                chunkIndex + 1, totalChunks, chunk.size(), jobId);
    }

    private void publishResolvedAudienceEvent(
            String tenantId,
            String jobId,
            EventEnvelope<Map<String, Object>> responseEvent) {
        try {
            eventPublisher.publish(AppConstants.TOPIC_AUDIENCE_RESOLVED, partitionKey(tenantId, jobId), responseEvent).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to publish resolved audience for job " + jobId, cause);
        }
    }

    private void forEachCandidatePage(
            String tenantId,
            String workspaceId,
            AudienceCandidateCriteria criteria,
            Consumer<List<Subscriber>> pageConsumer) {
        String lastSeenId = null;
        List<Subscriber> page;
        do {
            page = audienceCandidateRepository.findNextCandidates(
                    tenantId,
                    workspaceId,
                    criteria,
                    lastSeenId,
                    PageRequest.of(0, RESOLVED_AUDIENCE_CHUNK_SIZE));
            if (page.isEmpty()) {
                break;
            }
            lastSeenId = page.get(page.size() - 1).getId();
            pageConsumer.accept(page);
        } while (page.size() == RESOLVED_AUDIENCE_CHUNK_SIZE);
    }

    private List<Subscriber> filterEligibleSubscribers(String tenantId, String workspaceId, List<Subscriber> subscribers) {
        List<String> emailsToCheck = subscribers.stream()
                .map(Subscriber::getEmail)
                .distinct()
                .collect(Collectors.toList());
        Set<String> suppressedEmails = deliverabilityClient.checkSuppressedEmails(tenantId, workspaceId, emailsToCheck);

        return subscribers.stream()
                .filter(sub -> !suppressedEmails.contains(sub.getEmail()))
                .filter(sendEligibilityService::isSendEligible)
                .toList();
    }

    private Map<String, String> toResolvedSubscriberPayload(Subscriber subscriber) {
        return Map.of(
            "subscriberId", subscriber.getId(),
            "email", subscriber.getEmail(),
            "subscriberKey", subscriber.getSubscriberKey()
        );
    }

    private String resolvedChunkId(String jobId, int chunkIndex) {
        return jobId + ":audience:" + chunkIndex;
    }

    private String partitionKey(String tenantId, String jobId) {
        return jobId == null || jobId.isBlank() ? tenantId : jobId;
    }

    private record PublishingResult(int resolvedCount, int filteredCount) {
    }

    private record PendingChunk(int chunkIndex, List<Map<String, String>> subscribers) {
    }

    private static class PublishingState {
        private final List<Map<String, String>> currentChunk = new ArrayList<>(RESOLVED_AUDIENCE_CHUNK_SIZE);
        private int nextChunkIndex;
        private int totalSubscribers;
        private int filteredCount;
        private PendingChunk pendingChunk;

        private List<Map<String, String>> currentChunk() {
            return currentChunk;
        }

        private List<Map<String, String>> drainChunk() {
            List<Map<String, String>> drained = List.copyOf(currentChunk);
            currentChunk.clear();
            return drained;
        }

        private int nextChunkIndex() {
            return nextChunkIndex++;
        }

        private PendingChunk pendingChunk() {
            return pendingChunk;
        }

        private PendingChunk replacePendingChunk(PendingChunk chunk) {
            PendingChunk previous = pendingChunk;
            pendingChunk = chunk;
            return previous;
        }

        private int totalChunks() {
            return nextChunkIndex;
        }

        private void incrementTotalSubscribers() {
            totalSubscribers++;
        }

        private int totalSubscribers() {
            return totalSubscribers;
        }

        private void addFiltered(int count) {
            filteredCount += count;
        }

        private int filteredCount() {
            return filteredCount;
        }
    }
}

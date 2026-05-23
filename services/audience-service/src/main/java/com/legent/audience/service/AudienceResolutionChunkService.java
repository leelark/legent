package com.legent.audience.service;

import com.legent.audience.domain.AudienceResolutionChunk;
import com.legent.audience.repository.AudienceResolutionChunkRepository;
import com.legent.common.exception.NotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AudienceResolutionChunkService {

    public static final String STORAGE_BACKEND = "audience_resolution_chunks";
    public static final String REFERENCE_TYPE = "AUDIENCE_SERVICE_CHUNK";

    private final AudienceResolutionChunkRepository repository;

    @Transactional
    public ChunkReference storeChunk(
            String tenantId,
            String workspaceId,
            String campaignId,
            String jobId,
            String chunkId,
            int chunkIndex,
            int totalChunks,
            int totalResolvedSubscribers,
            boolean isLastChunk,
            List<Map<String, String>> subscribers) {
        String scopedTenantId = requireText("tenantId", tenantId);
        String scopedWorkspaceId = requireText("workspaceId", workspaceId);
        String scopedCampaignId = requireText("campaignId", campaignId);
        String scopedJobId = requireText("jobId", jobId);
        String scopedChunkId = requireText("chunkId", chunkId);
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex cannot be negative");
        }

        List<Map<String, String>> normalizedSubscribers = normalizeSubscribers(subscribers);
        AudienceResolutionChunk chunk = repository
                .findByTenantIdAndWorkspaceIdAndJobIdAndChunkIdAndDeletedAtIsNull(
                        scopedTenantId,
                        scopedWorkspaceId,
                        scopedJobId,
                        scopedChunkId)
                .orElseGet(AudienceResolutionChunk::new);
        chunk.setTenantId(scopedTenantId);
        chunk.setWorkspaceId(scopedWorkspaceId);
        chunk.setCampaignId(scopedCampaignId);
        chunk.setJobId(scopedJobId);
        chunk.setChunkId(scopedChunkId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkSize(normalizedSubscribers.size());
        chunk.setTotalChunks(totalChunks);
        chunk.setTotalResolvedSubscribers(totalResolvedSubscribers);
        chunk.setLastChunk(isLastChunk);
        chunk.setSubscriberPayload(normalizedSubscribers);
        AudienceResolutionChunk saved = repository.save(chunk);
        return new ChunkReference(
                saved.getChunkId(),
                STORAGE_BACKEND,
                REFERENCE_TYPE,
                saved.getChunkSize(),
                "/api/v1/audience-resolution-chunks/" + saved.getChunkId() + "/internal");
    }

    @Transactional(readOnly = true)
    public ChunkResponse getChunk(String tenantId, String workspaceId, String jobId, String chunkId) {
        String scopedTenantId = requireText("tenantId", tenantId);
        String scopedWorkspaceId = requireText("workspaceId", workspaceId);
        String scopedJobId = requireText("jobId", jobId);
        String scopedChunkId = requireText("chunkId", chunkId);
        AudienceResolutionChunk chunk = repository
                .findByTenantIdAndWorkspaceIdAndJobIdAndChunkIdAndDeletedAtIsNull(
                        scopedTenantId,
                        scopedWorkspaceId,
                        scopedJobId,
                        scopedChunkId)
                .orElseThrow(() -> new NotFoundException("AudienceResolutionChunk", scopedChunkId));
        return new ChunkResponse(
                chunk.getTenantId(),
                chunk.getWorkspaceId(),
                chunk.getCampaignId(),
                chunk.getJobId(),
                chunk.getChunkId(),
                chunk.getChunkIndex(),
                chunk.getChunkSize(),
                chunk.getTotalChunks(),
                chunk.getTotalResolvedSubscribers(),
                chunk.isLastChunk(),
                normalizeSubscribers(chunk.getSubscriberPayload()));
    }

    private List<Map<String, String>> normalizeSubscribers(List<Map<String, String>> subscribers) {
        if (subscribers == null || subscribers.isEmpty()) {
            return List.of();
        }
        return subscribers.stream()
                .map(this::normalizeSubscriber)
                .toList();
    }

    private Map<String, String> normalizeSubscriber(Map<String, String> subscriber) {
        if (subscriber == null || subscriber.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        subscriber.forEach((key, value) -> {
            if (key != null && value != null) {
                normalized.put(key, value);
            }
        });
        return Map.copyOf(normalized);
    }

    private String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required for audience resolution chunk storage");
        }
        return value.trim();
    }

    public record ChunkReference(String chunkId,
                                 String storageBackend,
                                 String referenceType,
                                 int chunkSize,
                                 String chunkUri) {
    }

    public record ChunkResponse(String tenantId,
                                String workspaceId,
                                String campaignId,
                                String jobId,
                                String chunkId,
                                int chunkIndex,
                                int chunkSize,
                                int totalChunks,
                                int totalResolvedSubscribers,
                                boolean isLastChunk,
                                List<Map<String, String>> subscribers) {
    }
}

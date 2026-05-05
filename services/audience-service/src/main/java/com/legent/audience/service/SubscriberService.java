package com.legent.audience.service;

import com.legent.common.constant.AppConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.Map;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.legent.audience.domain.Subscriber;
import com.legent.audience.repository.ConsentRecordRepository;
import com.legent.audience.repository.DoubleOptInTokenRepository;
import com.legent.audience.repository.ListMembershipRepository;
import com.legent.audience.repository.SegmentMembershipRepository;
import com.legent.audience.dto.SubscriberDto;
import com.legent.audience.mapper.SubscriberMapper;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.cache.service.CacheService;
import com.legent.cache.service.TenantCacheKeyGenerator;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.audience.event.SubscriberEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Core subscriber lifecycle operations.
 * Handles CRUD, deduplication, bulk upsert, and search.
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class SubscriberService {

    private final SubscriberRepository subscriberRepository;
    private final SubscriberMapper subscriberMapper;
    private final CacheService cacheService;
    private final SubscriberEventPublisher eventPublisher;
    private final ListMembershipRepository listMembershipRepository;
    private final SegmentMembershipRepository segmentMembershipRepository;
    private final ConsentRecordRepository consentRecordRepository;
    private final DoubleOptInTokenRepository doubleOptInTokenRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final Duration CACHE_TTL = Duration.ofSeconds(AppConstants.CACHE_SUBSCRIBER_TTL_SECONDS);
    private static final int BULK_CHUNK_SIZE = 100; // LEGENT-HIGH-001: Process in chunks to avoid all-or-nothing rollback

    @Lazy
    @Autowired
    private SubscriberService self;

    @Transactional(readOnly = true)
    public SubscriberDto.Response getById(String id) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        String cacheKey = TenantCacheKeyGenerator.key(AppConstants.CACHE_SUBSCRIBER_PREFIX, id);

        return cacheService.get(cacheKey, SubscriberDto.Response.class)
                .orElseGet(() -> {
                    Subscriber sub = subscriberRepository.findById(id)
                            .filter(s -> s.getTenantId().equals(tenantId) && workspaceId.equals(s.getWorkspaceId()) && !s.isDeleted())
                            .orElseThrow(() -> new NotFoundException("Subscriber", id));
                    SubscriberDto.Response resp = subscriberMapper.toResponse(sub);
                    cacheService.set(cacheKey, resp, CACHE_TTL);
                    return resp;
                });
    }

    @Transactional(readOnly = true)
    public SubscriberDto.Response getBySubscriberKey(String subscriberKey) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        Subscriber sub = subscriberRepository.findByTenantIdAndWorkspaceIdAndSubscriberKeyAndDeletedAtIsNull(tenantId, workspaceId, subscriberKey)
                .orElseThrow(() -> new NotFoundException("Subscriber", "subscriberKey=" + subscriberKey));
        return subscriberMapper.toResponse(sub);
    }

    @Transactional(readOnly = true)
    public Page<SubscriberDto.Response> list(Pageable pageable) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        return subscriberRepository.findAllByTenantAndWorkspace(tenantId, workspaceId, pageable)
                .map(subscriberMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<SubscriberDto.Response> search(String query, String status, Pageable pageable) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        if (status != null && !status.isBlank()) {
            Subscriber.SubscriberStatus parsedStatus = parseStatus(status);
            return subscriberRepository.findByTenantAndStatus(
                    tenantId, workspaceId, parsedStatus, pageable
            ).map(subscriberMapper::toResponse);
        }
        if (query != null && !query.isBlank()) {
            return subscriberRepository.searchByTenantAndWorkspace(tenantId, workspaceId, query.trim(), pageable)
                    .map(subscriberMapper::toResponse);
        }
        return list(pageable);
    }

    @Transactional
    public SubscriberDto.Response create(SubscriberDto.CreateRequest request) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        String normalizedEmail = normalizeEmail(request.getEmail());
        String resolvedKey = (request.getSubscriberKey() == null || request.getSubscriberKey().isBlank())
                ? generateDeterministicSubscriberKey(normalizedEmail, workspaceId)
                : request.getSubscriberKey().trim();

        if (subscriberRepository.existsByTenantIdAndSubscriberKeyAndDeletedAtIsNull(tenantId, resolvedKey)) {
            throw new ConflictException("Subscriber", "subscriberKey", resolvedKey);
        }

        if (subscriberRepository.findByTenantIdAndWorkspaceIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, workspaceId, normalizedEmail).isPresent()) {
            throw new ConflictException("Subscriber", "email", normalizedEmail);
        }

        Subscriber entity = subscriberMapper.toEntity(request);
        entity.setTenantId(tenantId);
        entity.setWorkspaceId(workspaceId);
        entity.setSubscriberKey(resolvedKey);
        entity.setEmail(normalizedEmail);
        entity.setLifecycleStageAt(entity.getLifecycleStageAt() == null ? java.time.Instant.now() : entity.getLifecycleStageAt());
        normalizeForPersist(entity);
        Subscriber saved = subscriberRepository.save(entity);

        log.info("Subscriber created: key={}, id={}", saved.getSubscriberKey(), saved.getId());
        eventPublisher.publishCreated(saved);
        return subscriberMapper.toResponse(saved);
    }

    @Transactional
    public SubscriberDto.Response update(String id, SubscriberDto.UpdateRequest request) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        Subscriber existing = subscriberRepository.findById(id)
                .filter(s -> s.getTenantId().equals(tenantId) && workspaceId.equals(s.getWorkspaceId()) && !s.isDeleted())
                .orElseThrow(() -> new NotFoundException("Subscriber", id));

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String normalizedEmail = normalizeEmail(request.getEmail());
            subscriberRepository.findByTenantIdAndWorkspaceIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, workspaceId, normalizedEmail)
                    .filter(other -> !other.getId().equals(existing.getId()))
                    .ifPresent(other -> {
                        throw new ConflictException("Subscriber", "email", normalizedEmail);
                    });
        }

        subscriberMapper.updateEntity(request, existing);
        if (existing.getEmail() != null) {
            existing.setEmail(normalizeEmail(existing.getEmail()));
        }

        if (request.getStatus() != null) {
            existing.setStatus(parseStatus(request.getStatus()));
        }

        Subscriber saved = subscriberRepository.save(existing);
        invalidateCache(id);
        eventPublisher.publishUpdated(saved);
        log.info("Subscriber updated: id={}", id);
        return subscriberMapper.toResponse(saved);
    }

    @Transactional
    public void delete(String id) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        Subscriber existing = subscriberRepository.findById(id)
                .filter(s -> s.getTenantId().equals(tenantId) && workspaceId.equals(s.getWorkspaceId()) && !s.isDeleted())
                .orElseThrow(() -> new NotFoundException("Subscriber", id));

        existing.softDelete();
        subscriberRepository.save(existing);
        invalidateCache(id);
        eventPublisher.publishDeleted(existing);
        log.info("Subscriber deleted: id={}", id);
    }

    /**
     * LEGENT-HIGH-001: Bulk upsert with deduplication and chunk-based processing.
     * Processes subscribers in chunks of 100 to prevent all-or-nothing transaction rollback.
     * Each chunk is a separate transaction - failure in one chunk doesn't affect others.
     * Dedup order: subscriber_key → email → alternate key.
     */
    public SubscriberDto.BulkUpsertResponse bulkUpsert(SubscriberDto.BulkUpsertRequest request) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        int totalCreated = 0, totalUpdated = 0, totalErrors = 0;
        List<SubscriberDto.BulkError> allErrorDetails = new ArrayList<>();

        List<SubscriberDto.CreateRequest> subscribers = request.getSubscribers();
        int totalSize = subscribers.size();

        // Split into chunks of BULK_CHUNK_SIZE
        List<List<SubscriberDto.CreateRequest>> chunks = IntStream.range(0, (totalSize + BULK_CHUNK_SIZE - 1) / BULK_CHUNK_SIZE)
                .mapToObj(i -> subscribers.subList(i * BULK_CHUNK_SIZE, Math.min((i + 1) * BULK_CHUNK_SIZE, totalSize)))
                .collect(Collectors.toList());

        log.info("Bulk upsert: processing {} subscribers in {} chunks of max {} each",
                totalSize, chunks.size(), BULK_CHUNK_SIZE);

        int processedCount = 0;
        for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
            List<SubscriberDto.CreateRequest> chunk = chunks.get(chunkIndex);
            try {
                // Each chunk processed in separate transaction via self-proxy
                ChunkResult result = self.processChunk(tenantId, workspaceId, chunk, request.getDeduplicationKey(), request.isUpdateExisting(), processedCount);
                totalCreated += result.created;
                totalUpdated += result.updated;
                totalErrors += result.errors;
                allErrorDetails.addAll(result.errorDetails);
            } catch (Exception e) {
                log.error("Chunk {} failed: {}", chunkIndex, e.getMessage());
                // Mark all items in chunk as failed
                for (int i = 0; i < chunk.size(); i++) {
                    totalErrors++;
                    allErrorDetails.add(SubscriberDto.BulkError.builder()
                            .index(processedCount + i)
                            .subscriberKey(chunk.get(i).getSubscriberKey())
                            .message("Chunk processing failed: " + e.getMessage())
                            .build());
                }
            }
            processedCount += chunk.size();
        }

        log.info("Bulk upsert complete: created={}, updated={}, errors={}", totalCreated, totalUpdated, totalErrors);
        return SubscriberDto.BulkUpsertResponse.builder()
                .created(totalCreated)
                .updated(totalUpdated)
                .errors(totalErrors)
                .errorDetails(allErrorDetails)
                .build();
    }

    /**
     * Process a single chunk within its own transaction.
     * Called via self-proxy to ensure @Transactional boundary is respected.
     */
    @Transactional
    public ChunkResult processChunk(String tenantId, String workspaceId, List<SubscriberDto.CreateRequest> chunk,
                                    String deduplicationKey, boolean updateExisting, int startIndex) {
        int created = 0, updated = 0, errors = 0;
        List<SubscriberDto.BulkError> errorDetails = new ArrayList<>();

        for (int i = 0; i < chunk.size(); i++) {
            SubscriberDto.CreateRequest subReq = chunk.get(i);
            int globalIndex = startIndex + i;
            try {
                Optional<Subscriber> existing = resolveExisting(tenantId, workspaceId, subReq, deduplicationKey);

                if (existing.isPresent() && updateExisting) {
                    Subscriber entity = existing.get();
                    applyUpdate(entity, subReq);
                    normalizeForPersist(entity);
                    subscriberRepository.save(entity);
                    updated++;
                } else if (existing.isEmpty()) {
                    Subscriber entity = subscriberMapper.toEntity(subReq);
                    entity.setTenantId(tenantId);
                    entity.setWorkspaceId(workspaceId);
                    entity.setEmail(normalizeEmail(subReq.getEmail()));
                    entity.setSubscriberKey(resolveSubscriberKey(subReq, workspaceId));
                    normalizeForPersist(entity);
                    subscriberRepository.save(entity);
                    created++;
                }
            } catch (Exception e) {
                errors++;
                errorDetails.add(SubscriberDto.BulkError.builder()
                        .index(globalIndex)
                        .subscriberKey(subReq.getSubscriberKey())
                        .message(e.getMessage())
                        .build());
            }
        }

        return new ChunkResult(created, updated, errors, errorDetails);
    }

    private record ChunkResult(int created, int updated, int errors, List<SubscriberDto.BulkError> errorDetails) {}

    @Transactional(readOnly = true)
    public long count() {
        return subscriberRepository.countByTenantAndWorkspace(AudienceScope.tenantId(), AudienceScope.workspaceId());
    }

    @Transactional
    public SubscriberDto.Response merge(SubscriberDto.MergeRequest request) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        Subscriber winner = subscriberRepository.findByTenantIdAndWorkspaceIdAndId(tenantId, workspaceId, request.getWinnerSubscriberId())
                .orElseThrow(() -> new NotFoundException("Subscriber", request.getWinnerSubscriberId()));

        for (String mergedId : request.getMergedSubscriberIds()) {
            if (winner.getId().equals(mergedId)) {
                continue;
            }
            Subscriber merged = subscriberRepository.findByTenantIdAndWorkspaceIdAndId(tenantId, workspaceId, mergedId)
                    .orElseThrow(() -> new NotFoundException("Subscriber", mergedId));

            mergeProfile(winner, merged);

            listMembershipRepository.reassignSubscriber(tenantId, workspaceId, merged.getId(), winner.getId());
            segmentMembershipRepository.reassignSubscriber(tenantId, workspaceId, merged.getId(), winner.getId());
            consentRecordRepository.reassignSubscriber(tenantId, workspaceId, merged.getId(), winner.getId());
            doubleOptInTokenRepository.reassignSubscriber(tenantId, workspaceId, merged.getId(), winner.getId());

            merged.softDelete();
            subscriberRepository.save(merged);
            writeMergeLog(tenantId, workspaceId, winner.getId(), merged.getId(), request.getReason());
        }

        Subscriber saved = subscriberRepository.save(winner);
        appendTimeline(saved, "PROFILE_MERGED", Map.of(
                "reason", request.getReason() == null ? "MANUAL_MERGE" : request.getReason(),
                "mergedSubscriberIds", request.getMergedSubscriberIds()
        ));
        return subscriberMapper.toResponse(saved);
    }

    @Transactional
    public long bulkAction(SubscriberDto.BulkActionRequest request) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        List<Subscriber> subscribers = subscriberRepository.findByTenantIdAndWorkspaceIdAndIdInAndDeletedAtIsNull(
                tenantId, workspaceId, request.getSubscriberIds());
        String action = request.getAction().toUpperCase(Locale.ROOT);
        long affected = 0;
        for (Subscriber subscriber : subscribers) {
            switch (action) {
                case "DELETE" -> subscriber.softDelete();
                case "BLOCK" -> subscriber.setStatus(Subscriber.SubscriberStatus.BLOCKED);
                case "UNBLOCK", "ACTIVATE" -> subscriber.setStatus(Subscriber.SubscriberStatus.ACTIVE);
                case "INACTIVE" -> subscriber.setStatus(Subscriber.SubscriberStatus.INACTIVE);
                default -> throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Unsupported action: " + request.getAction());
            }
            appendTimeline(subscriber, "BULK_ACTION", Map.of("action", action));
            subscriberRepository.save(subscriber);
            affected++;
        }
        return affected;
    }

    @Transactional
    public SubscriberDto.Response updateLifecycle(String subscriberId, SubscriberDto.LifecycleUpdateRequest request) {
        Subscriber subscriber = getScopedSubscriber(subscriberId);
        subscriber.setLifecycleStage(request.getStage().toUpperCase(Locale.ROOT));
        subscriber.setLifecycleStageAt(java.time.Instant.now());
        appendTimeline(subscriber, "LIFECYCLE_STAGE_UPDATED", Map.of("stage", subscriber.getLifecycleStage()));
        Subscriber saved = subscriberRepository.save(subscriber);
        return subscriberMapper.toResponse(saved);
    }

    @Transactional
    public SubscriberDto.Response updateScore(String subscriberId, SubscriberDto.ScoreUpdateRequest request) {
        Subscriber subscriber = getScopedSubscriber(subscriberId);
        if (request.getOpenScore() != null) subscriber.setOpenScore(request.getOpenScore());
        if (request.getClickScore() != null) subscriber.setClickScore(request.getClickScore());
        if (request.getConversionScore() != null) subscriber.setConversionScore(request.getConversionScore());
        if (request.getRecencyScore() != null) subscriber.setRecencyScore(request.getRecencyScore());
        if (request.getFrequencyScore() != null) subscriber.setFrequencyScore(request.getFrequencyScore());
        if (request.getEngagementScore() != null) subscriber.setEngagementScore(request.getEngagementScore());
        if (request.getActivityScore() != null) subscriber.setActivityScore(request.getActivityScore());
        subscriber.setTotalScore(
                subscriber.getOpenScore()
                        + subscriber.getClickScore()
                        + subscriber.getConversionScore()
                        + subscriber.getRecencyScore()
                        + subscriber.getFrequencyScore()
                        + subscriber.getEngagementScore()
                        + subscriber.getActivityScore());
        appendTimeline(subscriber, "SCORE_UPDATED", Map.of("totalScore", subscriber.getTotalScore()));
        Subscriber saved = subscriberRepository.save(subscriber);
        return subscriberMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SubscriberDto.ActivityTimelineResponse activity(String subscriberId) {
        Subscriber subscriber = getScopedSubscriber(subscriberId);
        return SubscriberDto.ActivityTimelineResponse.builder()
                .subscriberId(subscriberId)
                .entries(subscriber.getTimeline() == null ? List.of() : subscriber.getTimeline())
                .build();
    }

    // ── Helpers ──

    private Optional<Subscriber> resolveExisting(String tenantId, String workspaceId, SubscriberDto.CreateRequest req, String deduplicationKey) {
        if ("email".equalsIgnoreCase(deduplicationKey)) {
            return subscriberRepository.findByTenantIdAndWorkspaceIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, workspaceId, normalizeEmail(req.getEmail()));
        }
        // Default: subscriber_key
        String resolvedKey = resolveSubscriberKey(req, workspaceId);
        return subscriberRepository.findByTenantIdAndWorkspaceIdAndSubscriberKeyAndDeletedAtIsNull(tenantId, workspaceId, resolvedKey);
    }

    private void applyUpdate(Subscriber entity, SubscriberDto.CreateRequest req) {
        if (req.getEmail() != null) entity.setEmail(normalizeEmail(req.getEmail()));
        if (req.getFirstName() != null) entity.setFirstName(req.getFirstName());
        if (req.getLastName() != null) entity.setLastName(req.getLastName());
        if (req.getPhone() != null) entity.setPhone(req.getPhone());
        if (req.getCustomFields() != null) entity.setCustomFields(req.getCustomFields());
    }

    private void normalizeForPersist(Subscriber entity) {
        if (entity.getCustomFields() == null) entity.setCustomFields(new HashMap<>());
        if (entity.getChannelPreferences() == null) entity.setChannelPreferences(new HashMap<>());
        if (entity.getTags() == null) entity.setTags(new ArrayList<>());
        if (entity.getCategories() == null) entity.setCategories(new ArrayList<>());
        if (entity.getTimeline() == null) entity.setTimeline(new ArrayList<>());
        if (entity.getOwnershipScope() == null || entity.getOwnershipScope().isBlank()) entity.setOwnershipScope("WORKSPACE");
        if (entity.getLifecycleStage() == null || entity.getLifecycleStage().isBlank()) entity.setLifecycleStage("PROSPECT");
    }

    private void invalidateCache(String subscriberId) {
        String key = TenantCacheKeyGenerator.key(AppConstants.CACHE_SUBSCRIBER_PREFIX, subscriberId);
        cacheService.delete(key);
    }

    private Subscriber.SubscriberStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return Subscriber.SubscriberStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, 
                "Invalid status: " + status + ". Allowed values: " + java.util.Arrays.toString(Subscriber.SubscriberStatus.values())
            );
        }
    }

    private String resolveSubscriberKey(SubscriberDto.CreateRequest request, String workspaceId) {
        if (request.getSubscriberKey() != null && !request.getSubscriberKey().isBlank()) {
            return request.getSubscriberKey().trim();
        }
        return generateDeterministicSubscriberKey(normalizeEmail(request.getEmail()), workspaceId);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateDeterministicSubscriberKey(String normalizedEmail, String workspaceId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = normalizedEmail + "|" + workspaceId;
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return "sub-" + hex.substring(0, 24);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate subscriber key", e);
        }
    }

    private Subscriber getScopedSubscriber(String subscriberId) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        return subscriberRepository.findByTenantIdAndWorkspaceIdAndId(tenantId, workspaceId, subscriberId)
                .orElseThrow(() -> new NotFoundException("Subscriber", subscriberId));
    }

    private void mergeProfile(Subscriber winner, Subscriber merged) {
        if (winner.getFirstName() == null) winner.setFirstName(merged.getFirstName());
        if (winner.getLastName() == null) winner.setLastName(merged.getLastName());
        if (winner.getPhone() == null) winner.setPhone(merged.getPhone());
        if (winner.getLocale() == null) winner.setLocale(merged.getLocale());
        if (winner.getTimezone() == null) winner.setTimezone(merged.getTimezone());
        if (winner.getSource() == null) winner.setSource(merged.getSource());
        if (winner.getLeadSource() == null) winner.setLeadSource(merged.getLeadSource());
        if (winner.getAcquisitionChannel() == null) winner.setAcquisitionChannel(merged.getAcquisitionChannel());
        if (winner.getCampaignSource() == null) winner.setCampaignSource(merged.getCampaignSource());
        if (winner.getCustomFields() == null || winner.getCustomFields().isEmpty()) winner.setCustomFields(merged.getCustomFields());
        if (winner.getChannelPreferences() == null || winner.getChannelPreferences().isEmpty()) winner.setChannelPreferences(merged.getChannelPreferences());
        if (winner.getInternalNotes() == null) winner.setInternalNotes(merged.getInternalNotes());
    }

    private void appendTimeline(Subscriber subscriber, String eventType, Map<String, Object> metadata) {
        List<Map<String, Object>> timeline = subscriber.getTimeline();
        if (timeline == null) {
            timeline = new ArrayList<>();
        }
        timeline.add(Map.of(
                "type", eventType,
                "at", java.time.Instant.now().toString(),
                "metadata", metadata
        ));
        if (timeline.size() > 200) {
            timeline = new ArrayList<>(timeline.subList(timeline.size() - 200, timeline.size()));
        }
        subscriber.setTimeline(timeline);
    }

    private void writeMergeLog(String tenantId, String workspaceId, String winnerSubscriberId, String mergedSubscriberId, String reason) {
        jdbcTemplate.update(
                """
                INSERT INTO subscriber_merge_log
                (id, tenant_id, workspace_id, winner_subscriber_id, merged_subscriber_id, merge_reason, merged_fields, metadata, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, '{}'::jsonb, NOW(), NOW(), 0)
                """,
                com.legent.common.util.IdGenerator.newId(),
                tenantId,
                workspaceId,
                winnerSubscriberId,
                mergedSubscriberId,
                reason == null ? "MANUAL" : reason
        );
    }
}

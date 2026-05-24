package com.legent.audience.service;

import com.legent.audience.domain.Segment;
import com.legent.audience.domain.SegmentMembership;
import com.legent.audience.dto.SegmentDto;
import com.legent.audience.event.SegmentEventPublisher;
import com.legent.audience.repository.SegmentMembershipRepository;
import com.legent.audience.repository.SegmentRepository;
import com.legent.cache.service.CacheService;
import com.legent.common.constant.AppConstants;
import com.legent.common.exception.NotFoundException;
import com.legent.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Segmentation engine: compiles JSON rule trees into bounded parameterized SQL
 * over subscriber data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SegmentEvaluationService {

    private final SegmentRepository segmentRepository;
    private final SegmentMembershipRepository membershipRepository;
    private final CacheService cacheService;
    private final SegmentEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final org.springframework.beans.factory.ObjectProvider<SegmentEvaluationService> selfProvider;

    private static final Duration COUNT_CACHE_TTL = Duration.ofSeconds(AppConstants.CACHE_SEGMENT_COUNT_TTL_SECONDS);
    static final int SCHEDULED_RECOMPUTE_PAGE_SIZE = 100;
    static final int RECOMPUTE_MEMBER_PAGE_SIZE = 1_000;
    private static final String SEGMENT_MEMBERSHIP_INSERT_SQL = """
            INSERT INTO segment_memberships
            (id, tenant_id, workspace_id, ownership_scope, segment_id, subscriber_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    @Transactional(readOnly = true)
    public SegmentDto.CountPreview evaluateCount(String segmentId) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        Segment segment = segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, segmentId)
                .orElseThrow(() -> new NotFoundException("Segment", segmentId));
        PredictiveSegmentGovernanceService.requireApprovedForMaterialization(segment);
        SegmentRuleExecutionPlanCompiler.CompiledPlan plan = SegmentRuleExecutionPlanCompiler.compile(segment.getRules());
        SegmentDto.ExecutionPlanSummary executionPlan = toExecutionPlanSummary(plan);

        String cacheKey = AppConstants.CACHE_SEGMENT_COUNT_PREFIX + tenantId + ":" + workspaceId + ":" + segmentId;
        Optional<SegmentDto.CountPreview> cached = cacheService.get(cacheKey, SegmentDto.CountPreview.class);
        if (cached.isPresent()) {
            SegmentDto.CountPreview preview = cached.get();
            preview.setExecutionPlan(executionPlan);
            return preview;
        }

        long startMs = System.currentTimeMillis();
        long count = executeCountQuery(tenantId, workspaceId, plan);
        long durationMs = System.currentTimeMillis() - startMs;

        SegmentDto.CountPreview preview = SegmentDto.CountPreview.builder()
                .segmentId(segmentId)
                .count(count)
                .evaluationMs(durationMs)
                .executionPlan(executionPlan)
                .build();
        cacheService.set(cacheKey, preview, COUNT_CACHE_TTL);

        log.info("Segment evaluated: id={}, count={}, duration={}ms", segmentId, count, durationMs);
        return preview;
    }

    @Transactional(readOnly = true)
    public SegmentDto.ExecutionPlanPreview explainExecutionPlan(String segmentId) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        Segment segment = segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, segmentId)
                .orElseThrow(() -> new NotFoundException("Segment", segmentId));
        PredictiveSegmentGovernanceService.requireApprovedForMaterialization(segment);
        SegmentRuleExecutionPlanCompiler.CompiledPlan plan = SegmentRuleExecutionPlanCompiler.compile(segment.getRules());
        return SegmentDto.ExecutionPlanPreview.builder()
                .segmentId(segmentId)
                .executionPlan(toExecutionPlanSummary(plan))
                .build();
    }

    @Transactional(readOnly = true)
    public List<String> getSegmentMembers(String segmentId) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        return membershipRepository.findByTenantIdAndWorkspaceIdAndSegmentId(tenantId, workspaceId, segmentId).stream()
                .map(SegmentMembership::getSubscriberId)
                .collect(Collectors.toList());
    }

    @Async("segmentExecutor")
    @Transactional
    public void recompute(@org.springframework.lang.NonNull String segmentId, String tenantId, String workspaceId) {
        TenantContext.setTenantId(tenantId);
        TenantContext.setWorkspaceId(workspaceId);
        try {
            Segment segment = segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, segmentId)
                    .orElseThrow(() -> new NotFoundException("Segment", segmentId));
            PredictiveSegmentGovernanceService.requireApprovedForMaterialization(segment);

            segment.setStatus(Segment.SegmentStatus.COMPUTING);
            segmentRepository.save(segment);

            long startMs = System.currentTimeMillis();

            try {
                SegmentRuleExecutionPlanCompiler.CompiledPlan plan = SegmentRuleExecutionPlanCompiler.compile(segment.getRules());
                membershipRepository.deleteAllByTenantIdAndWorkspaceIdAndSegmentId(tenantId, workspaceId, segmentId);

                long memberCount = materializeMemberships(tenantId, workspaceId, segmentId, plan);

                long durationMs = System.currentTimeMillis() - startMs;
                segment.setMemberCount(memberCount);
                segment.setLastEvaluatedAt(Instant.now());
                segment.setEvaluationDurationMs(durationMs);
                segment.setStatus(Segment.SegmentStatus.ACTIVE);
                segmentRepository.save(segment);

                String cacheKey = AppConstants.CACHE_SEGMENT_COUNT_PREFIX + tenantId + ":" + workspaceId + ":" + segmentId;
                cacheService.delete(cacheKey);

                eventPublisher.publishRecomputed(segment);
                log.info("Segment recomputed: id={}, members={}, duration={}ms", segmentId, memberCount,
                        durationMs);

            } catch (Exception e) {
                segment.setStatus(Segment.SegmentStatus.ERROR);
                segmentRepository.save(segment);
                log.error("Segment recomputation failed: id={}", segmentId, e);
                throw e;
            }
        } finally {
            TenantContext.clear();
        }
    }

    @Scheduled(cron = "${legent.audience.segment.recompute-cron:0 */15 * * * *}")
    @Transactional(readOnly = true)
    public void scheduledRecompute() {
        String lastSegmentId = null;
        int scheduledCount = 0;

        while (true) {
            List<Segment> segments = segmentRepository.findScheduledSegmentsAfterId(
                    lastSegmentId,
                    PageRequest.of(0, SCHEDULED_RECOMPUTE_PAGE_SIZE));
            if (segments.isEmpty()) {
                break;
            }

            for (Segment seg : segments) {
                String segmentId = null;
                try {
                    TenantContext.setTenantId(seg.getTenantId());
                    TenantContext.setWorkspaceId(seg.getWorkspaceId());
                    segmentId = Objects.requireNonNull(seg.getId(), "Segment ID cannot be null");
                    lastSegmentId = segmentId;
                    scheduledCount++;
                    selfProvider.getObject().recompute(segmentId, seg.getTenantId(), seg.getWorkspaceId());
                } catch (Exception e) {
                    log.error("Failed to recompute segment {}: {}",
                            segmentId != null ? segmentId : seg.getId(),
                            e.getMessage());
                } finally {
                    TenantContext.clear();
                }
            }

            if (segments.size() < SCHEDULED_RECOMPUTE_PAGE_SIZE) {
                break;
            }
        }

        log.info("Scheduled segment recomputation: {} segments", scheduledCount);
    }

    private long executeCountQuery(String tenantId, String workspaceId, SegmentRuleExecutionPlanCompiler.CompiledPlan plan) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(s.id) FROM subscribers s WHERE s.tenant_id = :tid AND s.workspace_id = :wid AND s.deleted_at IS NULL");
        Map<String, Object> params = scopedParameters(tenantId, workspaceId, plan);

        appendCompiledWhereClause(sql, plan);

        Query query = entityManager.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        return ((Number) query.getSingleResult()).longValue();
    }

    private long materializeMemberships(
            String tenantId,
            String workspaceId,
            String segmentId,
            SegmentRuleExecutionPlanCompiler.CompiledPlan plan) {
        long memberCount = 0;
        String afterSubscriberId = null;

        while (true) {
            List<String> subscriberIds = executeQueryPage(
                    tenantId,
                    workspaceId,
                    plan,
                    afterSubscriberId,
                    RECOMPUTE_MEMBER_PAGE_SIZE);
            if (subscriberIds.isEmpty()) {
                return memberCount;
            }

            List<Object[]> batchArgs = new ArrayList<>(subscriberIds.size());
            Instant now = Instant.now();
            for (String subId : subscriberIds) {
                batchArgs.add(new Object[] {
                        com.legent.common.util.IdGenerator.newId(),
                        tenantId,
                        workspaceId,
                        "WORKSPACE",
                        segmentId,
                        subId,
                        now,
                        now
                });
            }
            jdbcTemplate.batchUpdate(SEGMENT_MEMBERSHIP_INSERT_SQL, batchArgs);

            memberCount += subscriberIds.size();
            afterSubscriberId = subscriberIds.get(subscriberIds.size() - 1);
            if (subscriberIds.size() < RECOMPUTE_MEMBER_PAGE_SIZE) {
                return memberCount;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> executeQueryPage(
            String tenantId,
            String workspaceId,
            SegmentRuleExecutionPlanCompiler.CompiledPlan plan,
            String afterSubscriberId,
            int pageSize) {
        StringBuilder sql = new StringBuilder(
                "SELECT s.id FROM subscribers s WHERE s.tenant_id = :tid AND s.workspace_id = :wid AND s.deleted_at IS NULL");
        Map<String, Object> params = scopedParameters(tenantId, workspaceId, plan);
        if (afterSubscriberId != null) {
            sql.append(" AND s.id > :afterSubscriberId");
            params.put("afterSubscriberId", afterSubscriberId);
        }

        appendCompiledWhereClause(sql, plan);
        sql.append(" ORDER BY s.id");

        Query query = entityManager.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        query.setMaxResults(pageSize);
        return query.getResultList().stream()
                .map(String.class::cast)
                .toList();
    }

    private Map<String, Object> scopedParameters(
            String tenantId,
            String workspaceId,
            SegmentRuleExecutionPlanCompiler.CompiledPlan plan) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tid", tenantId);
        params.put("wid", workspaceId);
        params.putAll(plan.parameters());
        return params;
    }

    private void appendCompiledWhereClause(StringBuilder sql, SegmentRuleExecutionPlanCompiler.CompiledPlan plan) {
        if (!plan.whereClause().isBlank()) {
            sql.append(" AND (").append(plan.whereClause()).append(")");
        }
    }

    private SegmentDto.ExecutionPlanSummary toExecutionPlanSummary(
            SegmentRuleExecutionPlanCompiler.CompiledPlan plan) {
        return SegmentDto.ExecutionPlanSummary.builder()
                .executionMode(SegmentRuleExecutionPlanCompiler.EXECUTION_MODE)
                .bounded(plan.bounded())
                .conditionCount(plan.conditionCount())
                .maxDepth(plan.maxDepth())
                .requiredIndexes(plan.requiredIndexes())
                .warnings(plan.warnings())
                .steps(plan.steps().stream()
                        .map(this::toExecutionPlanStep)
                        .toList())
                .build();
    }

    private SegmentDto.ExecutionPlanStep toExecutionPlanStep(SegmentRuleExecutionPlanCompiler.PlanStep step) {
        return SegmentDto.ExecutionPlanStep.builder()
                .path(step.path())
                .family(step.family())
                .field(step.field())
                .operator(step.operator())
                .strategy(step.strategy())
                .tenantWorkspaceScoped(step.tenantWorkspaceScoped())
                .indexedLookup(step.indexedLookup())
                .build();
    }
}

package com.legent.audience.service;

import com.legent.common.constant.AppConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import java.util.Map;

import java.time.Instant;

import com.legent.audience.domain.Segment;
import com.legent.audience.domain.SegmentMembership;
import com.legent.audience.dto.SegmentDto;
import com.legent.audience.event.SegmentEventPublisher;
import com.legent.audience.repository.SegmentMembershipRepository;
import com.legent.audience.repository.SegmentRepository;
import com.legent.cache.service.CacheService;
import com.legent.cache.service.TenantCacheKeyGenerator;
import com.legent.common.exception.NotFoundException;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Duration;

/**
 * Segmentation engine — parses JSON rule trees and evaluates them
 * against subscriber data using parameterized native SQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SegmentEvaluationService {

    private final SegmentRepository segmentRepository;
    private final SegmentMembershipRepository membershipRepository;
    private final CacheService cacheService;
    private final SegmentEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    private static final Duration COUNT_CACHE_TTL = Duration.ofSeconds(AppConstants.CACHE_SEGMENT_COUNT_TTL_SECONDS);

    /**
     * Real-time count preview — runs the query without materializing results.
     */
    @Transactional(readOnly = true)
    public SegmentDto.CountPreview evaluateCount(String segmentId) {
        String tenantId = TenantContext.getTenantId();
        Segment segment = segmentRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, segmentId)
                .orElseThrow(() -> new NotFoundException("Segment", segmentId));

        String cacheKey = TenantCacheKeyGenerator.key(AppConstants.CACHE_SEGMENT_COUNT_PREFIX, segmentId);
        Optional<SegmentDto.CountPreview> cached = cacheService.get(cacheKey, SegmentDto.CountPreview.class);
        if (cached.isPresent()) return cached.get();

        long startMs = System.currentTimeMillis();
        long count = executeCountQuery(tenantId, segment.getRules());
        long durationMs = System.currentTimeMillis() - startMs;

        SegmentDto.CountPreview preview = SegmentDto.CountPreview.builder()
                .segmentId(segmentId).count(count).evaluationMs(durationMs).build();
        cacheService.set(cacheKey, preview, COUNT_CACHE_TTL);

        log.info("Segment evaluated: id={}, count={}, duration={}ms", segmentId, count, durationMs);
        return preview;
    }

    /**
     * Full recomputation — materializes segment membership.
     */
    @Transactional(readOnly = true)
    public List<String> getSegmentMembers(String segmentId) {
        return membershipRepository.findBySegmentId(segmentId).stream()
                .map(SegmentMembership::getSubscriberId)
                .collect(Collectors.toList());
    }

    @Async("segmentExecutor")
    @Transactional
    public void recompute(String segmentId) {
        String tenantId = TenantContext.getTenantId();
        Segment segment = segmentRepository.findById(segmentId)
                .orElseThrow(() -> new NotFoundException("Segment", segmentId));

        segment.setStatus(Segment.SegmentStatus.COMPUTING);
        segmentRepository.save(segment);

        try {
            long startMs = System.currentTimeMillis();

            // Clear existing memberships
            membershipRepository.deleteAllBySegmentId(segmentId);

            // Execute query and materialize using batch insert
            List<String> subscriberIds = executeQuery(tenantId, segment.getRules());
            if (!subscriberIds.isEmpty()) {
                String sqlInsert = "INSERT INTO segment_memberships (id, tenant_id, segment_id, subscriber_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
                List<Object[]> batchArgs = new ArrayList<>();
                Instant now = Instant.now();
                for (String subId : subscriberIds) {
                    batchArgs.add(new Object[]{
                            com.legent.common.util.IdGenerator.newId(),
                            tenantId,
                            segmentId,
                            subId,
                            now,
                            now
                    });
                }
                jdbcTemplate.batchUpdate(sqlInsert, batchArgs);
            }

            long durationMs = System.currentTimeMillis() - startMs;
            segment.setMemberCount(subscriberIds.size());
            segment.setLastEvaluatedAt(Instant.now());
            segment.setEvaluationDurationMs(durationMs);
            segment.setStatus(Segment.SegmentStatus.ACTIVE);
            segmentRepository.save(segment);

            // Invalidate count cache
            String cacheKey = TenantCacheKeyGenerator.key(AppConstants.CACHE_SEGMENT_COUNT_PREFIX, segmentId);
            cacheService.delete(cacheKey);

            eventPublisher.publishRecomputed(segment);
            log.info("Segment recomputed: id={}, members={}, duration={}ms", segmentId, subscriberIds.size(), durationMs);

        } catch (Exception e) {
            segment.setStatus(Segment.SegmentStatus.ERROR);
            segmentRepository.save(segment);
            log.error("Segment recomputation failed: id={}", segmentId, e);
            throw e;
        }
    }

    /**
     * Scheduled recomputation of all enabled segments (every 15 minutes).
     */
    @Scheduled(cron = "${legent.audience.segment.recompute-cron:0 */15 * * * *}")
    @Transactional(readOnly = true)
    public void scheduledRecompute() {
        List<Segment> segments = segmentRepository.findScheduledSegments();
        log.info("Scheduled segment recomputation: {} segments", segments.size());

        for (Segment seg : segments) {
            try {
                TenantContext.setTenantId(seg.getTenantId());
                recompute(seg.getId());
            } catch (Exception e) {
                log.error("Failed to recompute segment {}: {}", seg.getId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    // ── Query Generation Engine ──

    private long executeCountQuery(String tenantId, Map<String, Object> rules) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(s.id) FROM subscribers s WHERE s.tenant_id = :tid AND s.deleted_at IS NULL");
        Map<String, Object> params = new HashMap<>();
        params.put("tid", tenantId);

        appendRuleConditions(sql, params, rules, 0);

        Query query = entityManager.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        return ((Number) query.getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    private List<String> executeQuery(String tenantId, Map<String, Object> rules) {
        StringBuilder sql = new StringBuilder("SELECT s.id FROM subscribers s WHERE s.tenant_id = :tid AND s.deleted_at IS NULL");
        Map<String, Object> params = new HashMap<>();
        params.put("tid", tenantId);

        appendRuleConditions(sql, params, rules, 0);

        Query query = entityManager.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        return query.getResultList();
    }

    /**
     * Recursively builds SQL conditions from a JSON rule tree.
     * ALL user input is parameterized — never concatenated.
     */
    @SuppressWarnings("unchecked")
    private int appendRuleConditions(StringBuilder sql, Map<String, Object> params, Map<String, Object> rules, int paramIdx) {
        String operator = (String) rules.getOrDefault("operator", "AND");
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) rules.get("conditions");
        List<Map<String, Object>> groups = (List<Map<String, Object>>) rules.get("groups");

        List<String> clauses = new ArrayList<>();

        if (conditions != null) {
            for (Map<String, Object> cond : conditions) {
                String field = (String) cond.get("field");
                String op = (String) cond.get("op");
                Object value = cond.get("value");
                String paramName = "p" + (paramIdx++);

                String clause = buildCondition(field, op, value, paramName, params);
                if (clause != null) clauses.add(clause);
            }
        }

        if (groups != null) {
            for (Map<String, Object> group : groups) {
                StringBuilder subSql = new StringBuilder();
                Map<String, Object> subParams = new HashMap<>();
                paramIdx = appendRuleConditions(subSql, subParams, group, paramIdx);
                params.putAll(subParams);
                if (!subSql.isEmpty()) clauses.add("(" + subSql + ")");
            }
        }

        if (!clauses.isEmpty()) {
            String joined = String.join(" " + operator + " ", clauses);
            sql.append(" AND (").append(joined).append(")");
        }

        return paramIdx;
    }

    private String buildCondition(String field, String op, Object value, String paramName, Map<String, Object> params) {
        String column = mapFieldToColumn(field);
        if (column == null) return null;

        return switch (op.toUpperCase()) {
            case "EQUALS" -> {
                params.put(paramName, value);
                yield column + " = :" + paramName;
            }
            case "NOT_EQUALS" -> {
                params.put(paramName, value);
                yield column + " != :" + paramName;
            }
            case "CONTAINS" -> {
                params.put(paramName, "%" + value + "%");
                yield column + " ILIKE :" + paramName;
            }
            case "STARTS_WITH" -> {
                params.put(paramName, value + "%");
                yield column + " ILIKE :" + paramName;
            }
            case "ENDS_WITH" -> {
                params.put(paramName, "%" + value);
                yield column + " ILIKE :" + paramName;
            }
            case "GREATER_THAN" -> {
                params.put(paramName, value);
                yield column + " > :" + paramName;
            }
            case "LESS_THAN" -> {
                params.put(paramName, value);
                yield column + " < :" + paramName;
            }
            case "IS_NULL" -> column + " IS NULL";
            case "IS_NOT_NULL" -> column + " IS NOT NULL";
            case "IN_LIST" -> {
                params.put(paramName, value);
                yield "EXISTS (SELECT 1 FROM list_memberships lm WHERE lm.subscriber_id = s.id AND lm.list_id = :" + paramName + " AND lm.status = 'ACTIVE')";
            }
            case "NOT_IN_LIST" -> {
                params.put(paramName, value);
                yield "NOT EXISTS (SELECT 1 FROM list_memberships lm WHERE lm.subscriber_id = s.id AND lm.list_id = :" + paramName + " AND lm.status = 'ACTIVE')";
            }
            case "IN_SEGMENT" -> {
                params.put(paramName, value);
                yield "EXISTS (SELECT 1 FROM segment_memberships sm WHERE sm.subscriber_id = s.id AND sm.segment_id = :" + paramName + ")";
            }
            default -> null;
        };
    }

    private String mapFieldToColumn(String field) {
        return switch (field.toLowerCase()) {
            case "email" -> "s.email";
            case "first_name", "firstname" -> "s.first_name";
            case "last_name", "lastname" -> "s.last_name";
            case "status" -> "s.status";
            case "source" -> "s.source";
            case "locale" -> "s.locale";
            case "timezone" -> "s.timezone";
            case "phone" -> "s.phone";
            case "subscriber_key", "subscriberkey" -> "s.subscriber_key";
            case "created_at", "createdat" -> "s.created_at";
            case "subscribed_at", "subscribedat" -> "s.subscribed_at";
            case "last_activity_at", "lastactivityat" -> "s.last_activity_at";
            case "list_membership" -> null; // handled specially in buildCondition
            // Fallback: Assume it's a dynamic custom attribute stored in the JSONB column
            default -> {
                if (field == null || !field.matches("^[a-zA-Z0-9_]+$")) {
                    throw new IllegalArgumentException("Invalid field name: " + field);
                }
                yield "s.custom_fields->>'" + field + "'";
            }
        };
    }
}

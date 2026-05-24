package com.legent.tracking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {
    static final Duration DEFAULT_EVENT_COUNT_WINDOW = Duration.ofHours(168);
    static final Duration MAX_EVENT_COUNT_WINDOW = Duration.ofDays(31);
    static final int DEFAULT_TIMELINE_BUCKET_LIMIT = 168;
    static final int MAX_TIMELINE_BUCKET_LIMIT = 744;
    static final int DEFAULT_HOUR_ROLLUP_BUCKET_LIMIT = 168;
    static final int DEFAULT_DAY_ROLLUP_BUCKET_LIMIT = 90;
    static final int MAX_HOUR_ROLLUP_BUCKET_LIMIT = 744;
    static final int MAX_DAY_ROLLUP_BUCKET_LIMIT = 366;
    public static final String QUERY_SEMANTICS_CANONICAL_EVENT_ID = "CANONICAL_EVENT_ID";
    public static final String QUERY_SEMANTICS_PHYSICAL_RAW_ROW = "PHYSICAL_RAW_ROW";
    public static final String SOURCE_DATASET_RAW_EVENTS = "raw_events";
    public static final List<String> CANONICAL_DEDUPE_KEY = List.of("tenant_id", "workspace_id", "event_type", "id");

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getEventCounts(String tenantId, String workspaceId) {
        return getEventCounts(tenantId, workspaceId, null, null);
    }

    public List<Map<String, Object>> getEventCounts(String tenantId,
                                                    String workspaceId,
                                                    Instant startAt,
                                                    Instant endAt) {
        if (tenantId == null || tenantId.isBlank() || workspaceId == null || workspaceId.isBlank()) {
            log.warn("Tenant/workspace context missing; returning empty event counts");
            return new ArrayList<>();
        }
        TimeWindow timeWindow = boundedTimeWindow(startAt, endAt, DEFAULT_EVENT_COUNT_WINDOW, MAX_EVENT_COUNT_WINDOW);
        if (timeWindow == null) {
            log.warn("Invalid event count window for tenant {} workspace {}", tenantId, workspaceId);
            return new ArrayList<>();
        }
        try {
            return jdbcTemplate.queryForList("""
                SELECT event_type, COALESCE(count(*), 0) AS count
                %s
                GROUP BY event_type
            """.formatted(canonicalRawEventsSource("""
                    tenant_id = ? AND workspace_id = ?
                      AND "timestamp" >= ? AND "timestamp" <= ?
                    """)), tenantId, workspaceId, timeWindow.startAt(), timeWindow.endAt());
        } catch (DataAccessException e) {
            log.error("Failed to query event counts for tenant {} workspace {}", tenantId, workspaceId, e);
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> getEventTimeline(String tenantId, String workspaceId, String eventType) {
        return getEventTimeline(tenantId, workspaceId, eventType, null, null, null);
    }

    public List<Map<String, Object>> getEventTimeline(String tenantId,
                                                      String workspaceId,
                                                      String eventType,
                                                      Instant startAt,
                                                      Instant endAt,
                                                      Integer buckets) {
        if (tenantId == null || tenantId.isBlank()
                || workspaceId == null || workspaceId.isBlank()
                || eventType == null || eventType.isBlank()) {
            log.warn("Tenant/workspace/eventType missing; returning empty timeline");
            return new ArrayList<>();
        }
        String normalizedEventType = eventType.trim().toUpperCase(java.util.Locale.ROOT);
        int bucketLimit = boundedLimit(buckets, DEFAULT_TIMELINE_BUCKET_LIMIT, MAX_TIMELINE_BUCKET_LIMIT);
        TimeWindow timeWindow = boundedTimeWindow(startAt, endAt, Duration.ofHours(bucketLimit));
        if (timeWindow == null) {
            log.warn("Invalid event timeline window for tenant {} workspace {} and eventType {}",
                    tenantId, workspaceId, normalizedEventType);
            return new ArrayList<>();
        }
        try {
            return jdbcTemplate.queryForList("""
                SELECT date_trunc('hour', "timestamp") AS hour, COALESCE(count(*), 0) AS count
                %s
                GROUP BY hour
                ORDER BY hour
                LIMIT ?
            """.formatted(canonicalRawEventsSource("""
                    tenant_id = ? AND workspace_id = ? AND event_type = ?
                      AND "timestamp" >= ? AND "timestamp" <= ?
                    """)), tenantId, workspaceId, normalizedEventType, timeWindow.startAt(), timeWindow.endAt(), bucketLimit);
        } catch (DataAccessException e) {
            log.error("Failed to query event timeline for tenant {} workspace {} and eventType {}",
                    tenantId, workspaceId, eventType, e);
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> getExperimentMetrics(String tenantId, String workspaceId, String campaignId, String experimentId) {
        if (tenantId == null || tenantId.isBlank()
                || workspaceId == null || workspaceId.isBlank()
                || campaignId == null || campaignId.isBlank()
                || experimentId == null || experimentId.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return jdbcTemplate.queryForList("""
                SELECT
                    COALESCE(variant_id, 'HOLDOUT') AS variant_id,
                    COUNT(*) FILTER (WHERE event_type = 'OPEN') AS opens,
                    COUNT(*) FILTER (WHERE event_type = 'CLICK') AS clicks,
                    COUNT(*) FILTER (WHERE event_type = 'CONVERSION') AS conversions,
                    COALESCE(SUM(CASE
                        WHEN event_type = 'CONVERSION'
                         AND metadata IS NOT NULL
                         AND metadata->>'value' ~ '^[0-9]+(\\.[0-9]+)?$'
                        THEN (metadata->>'value')::numeric
                        ELSE 0
                    END), 0) AS revenue,
                    COUNT(*) FILTER (WHERE metadata ? 'customMetricName') AS custom_metric_count
                %s
                GROUP BY COALESCE(variant_id, 'HOLDOUT')
                ORDER BY variant_id
            """.formatted(canonicalRawEventsSource("""
                    tenant_id = ?
                      AND workspace_id = ?
                      AND campaign_id = ?
                      AND experiment_id = ?
                    """)), tenantId, workspaceId, campaignId, experimentId);
        } catch (DataAccessException e) {
            log.error("Failed to query experiment metrics for tenant {} workspace {} campaign {} experiment {}",
                    tenantId, workspaceId, campaignId, experimentId, e);
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> getJourneyGoalMetrics(String tenantId, String workspaceId, String workflowId) {
        if (tenantId == null || tenantId.isBlank()
                || workspaceId == null || workspaceId.isBlank()
                || workflowId == null || workflowId.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return jdbcTemplate.queryForList("""
                SELECT
                    COALESCE(goal_id, 'UNSPECIFIED') AS goal_id,
                    step_id,
                    path_id,
                    COALESCE(experiment_scope, 'JOURNEY') AS experiment_scope,
                    COUNT(*) AS conversions,
                    COUNT(DISTINCT subscriber_id) AS unique_subscribers,
                    COALESCE(SUM(CASE
                        WHEN metadata IS NOT NULL
                         AND metadata->>'value' ~ '^[0-9]+(\\.[0-9]+)?$'
                        THEN (metadata->>'value')::numeric
                        ELSE 0
                    END), 0) AS revenue
                %s
                GROUP BY COALESCE(goal_id, 'UNSPECIFIED'), step_id, path_id, COALESCE(experiment_scope, 'JOURNEY')
                ORDER BY conversions DESC, goal_id
                LIMIT 100
            """.formatted(canonicalRawEventsSource("""
                    tenant_id = ?
                      AND workspace_id = ?
                      AND workflow_id = ?
                      AND event_type = 'CONVERSION'
                    """)), tenantId, workspaceId, workflowId);
        } catch (DataAccessException e) {
            log.error("Failed to query journey goal metrics for tenant {} workspace {} workflow {}",
                    tenantId, workspaceId, workflowId, e);
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> getRollups(String tenantId, String workspaceId, String campaignId, String grain) {
        return getRollups(tenantId, workspaceId, campaignId, grain, null, null, null);
    }

    public List<Map<String, Object>> getRollups(String tenantId,
                                                String workspaceId,
                                                String campaignId,
                                                String grain,
                                                Instant startAt,
                                                Instant endAt,
                                                Integer buckets) {
        if (tenantId == null || tenantId.isBlank() || workspaceId == null || workspaceId.isBlank()) {
            return new ArrayList<>();
        }
        String normalizedGrain = "day".equalsIgnoreCase(grain) ? "day" : "hour";
        int bucketLimit = rollupBucketLimit(buckets, normalizedGrain);
        TimeWindow timeWindow = boundedTimeWindow(startAt, endAt, rollupWindowDuration(normalizedGrain, bucketLimit));
        if (timeWindow == null) {
            log.warn("Invalid rollup window for tenant {} workspace {}", tenantId, workspaceId);
            return new ArrayList<>();
        }
        List<Object> params = new ArrayList<>(List.of(tenantId, workspaceId, timeWindow.startAt(), timeWindow.endAt()));
        StringBuilder sourceWhere = new StringBuilder("""
                tenant_id = ? AND workspace_id = ?
                  AND "timestamp" >= ? AND "timestamp" <= ?
                """);
        if (campaignId != null && !campaignId.isBlank()) {
            sourceWhere.append(" AND campaign_id = ?");
            params.add(campaignId);
        }
        StringBuilder sql = new StringBuilder("""
                SELECT campaign_id,
                       date_trunc('%s', "timestamp") AS bucket,
                       COUNT(*) FILTER (WHERE event_type = 'SEND') AS sends,
                       COUNT(*) FILTER (WHERE event_type = 'DELIVERED') AS delivered,
                       COUNT(*) FILTER (WHERE event_type = 'OPEN') AS opens,
                       COUNT(*) FILTER (WHERE event_type = 'CLICK') AS clicks,
                       COUNT(*) FILTER (WHERE event_type = 'BOUNCE') AS bounces,
                       COUNT(*) FILTER (WHERE event_type = 'COMPLAINT') AS complaints,
                       COUNT(*) FILTER (WHERE event_type = 'UNSUBSCRIBE') AS unsubscribes,
                       COUNT(*) FILTER (WHERE event_type = 'CONVERSION') AS conversions
                %s
                """.formatted(normalizedGrain, canonicalRawEventsSource(sourceWhere.toString())));
        sql.append(" GROUP BY campaign_id, bucket ORDER BY bucket DESC, campaign_id LIMIT ?");
        params.add(bucketLimit);
        try {
            return jdbcTemplate.queryForList(sql.toString(), params.toArray());
        } catch (DataAccessException e) {
            log.error("Failed to query rollups for tenant {} workspace {}", tenantId, workspaceId, e);
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> exportEvents(String tenantId,
                                                  String workspaceId,
                                                  String campaignId,
                                                  List<String> eventTypes,
                                                  Instant startAt,
                                                  Instant endAt,
                                                  int limit) {
        if (tenantId == null || tenantId.isBlank() || workspaceId == null || workspaceId.isBlank()) {
            return new ArrayList<>();
        }
        int cappedLimit = Math.max(1, Math.min(limit, 10_000));
        List<Object> params = new ArrayList<>(List.of(tenantId, workspaceId));
        StringBuilder sql = new StringBuilder("""
                SELECT id, event_type, campaign_id, subscriber_id, message_id, experiment_id, variant_id,
                       holdout, experiment_scope, workflow_id, workflow_version, workflow_run_id, step_id,
                       path_id, goal_id, link_url, "timestamp", metadata
                FROM raw_events
                WHERE tenant_id = ? AND workspace_id = ?
                """);
        if (campaignId != null && !campaignId.isBlank()) {
            sql.append(" AND campaign_id = ?");
            params.add(campaignId);
        }
        if (eventTypes != null && !eventTypes.isEmpty()) {
            StringJoiner joiner = new StringJoiner(",", " AND event_type IN (", ")");
            for (String eventType : eventTypes) {
                joiner.add("?");
                params.add(eventType.toUpperCase(Locale.ROOT));
            }
            sql.append(joiner);
        }
        if (startAt != null) {
            sql.append(" AND \"timestamp\" >= ?");
            params.add(startAt);
        }
        if (endAt != null) {
            sql.append(" AND \"timestamp\" <= ?");
            params.add(endAt);
        }
        sql.append(" ORDER BY \"timestamp\" DESC LIMIT ?");
        params.add(cappedLimit);
        try {
            return jdbcTemplate.queryForList(sql.toString(), params.toArray());
        } catch (DataAccessException e) {
            log.error("Failed to export events for tenant {} workspace {}", tenantId, workspaceId, e);
            return new ArrayList<>();
        }
    }

    public String toCsv(List<Map<String, Object>> rows) {
        List<String> headers = List.of("id", "event_type", "campaign_id", "subscriber_id", "message_id",
                "experiment_id", "variant_id", "holdout", "experiment_scope", "workflow_id", "workflow_version",
                "workflow_run_id", "step_id", "path_id", "goal_id", "link_url", "timestamp", "metadata");
        StringBuilder csv = new StringBuilder(String.join(",", headers)).append('\n');
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < headers.size(); i++) {
                if (i > 0) {
                    csv.append(',');
                }
                csv.append(escapeCsv(row.get(headers.get(i))));
            }
            csv.append('\n');
        }
        return csv.toString();
    }

    public List<Map<String, Object>> taxonomy() {
        return List.of(
                taxonomy("SEND", "delivery", "Message send request accepted.", List.of("campaignId", "subscriberId", "messageId"), List.of("providerId", "jobId")),
                taxonomy("DELIVERED", "delivery", "Provider reported message delivery.", List.of("messageId"), List.of("providerMessageId")),
                taxonomy("OPEN", "engagement", "Tracking pixel loaded.", List.of("messageId", "subscriberId"), List.of("userAgent", "ipHash")),
                taxonomy("CLICK", "engagement", "Tracked link redirect clicked.", List.of("messageId", "subscriberId", "linkUrl"), List.of("linkId")),
                taxonomy("BOUNCE", "deliverability", "Provider reported bounce.", List.of("messageId"), List.of("bounceType", "smtpCode", "diagnostic")),
                taxonomy("COMPLAINT", "deliverability", "Recipient complaint or feedback loop event.", List.of("messageId"), List.of("fblProvider")),
                taxonomy("UNSUBSCRIBE", "consent", "Recipient unsubscribe event.", List.of("subscriberId"), List.of("source", "listId")),
                taxonomy("CONVERSION", "business", "Tracked conversion event.", List.of("eventName"), List.of("value", "currency", "experimentScope", "workflowId", "workflowRunId", "stepId", "pathId", "goalId"))
        );
    }

    public Map<String, Object> rawPhysicalCountsForCampaign(String tenantId, String workspaceId, String campaignId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT event_type, COUNT(*) AS count
                FROM raw_events
                WHERE tenant_id = ? AND workspace_id = ? AND campaign_id = ?
                GROUP BY event_type
                """, tenantId, workspaceId, campaignId);
        return countMap(rows);
    }

    public Map<String, Object> canonicalCountsForCampaign(String tenantId, String workspaceId, String campaignId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT event_type, COUNT(*) AS count
                %s
                GROUP BY event_type
                """.formatted(canonicalRawEventsSource("tenant_id = ? AND workspace_id = ? AND campaign_id = ?")),
                tenantId, workspaceId, campaignId);
        return countMap(rows);
    }

    public Map<String, Object> rawCountsForCampaign(String tenantId, String workspaceId, String campaignId) {
        return canonicalCountsForCampaign(tenantId, workspaceId, campaignId);
    }

    private Map<String, Object> countMap(List<Map<String, Object>> rows) {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            counts.put(String.valueOf(row.get("event_type")), row.get("count"));
        }
        return counts;
    }

    private String canonicalRawEventsSource(String whereClause) {
        return """
                FROM (
                    SELECT tenant_id,
                           workspace_id,
                           event_type,
                           id,
                           MIN(campaign_id) AS campaign_id,
                           MIN(subscriber_id) AS subscriber_id,
                           MIN(message_id) AS message_id,
                           MIN(experiment_id) AS experiment_id,
                           MIN(variant_id) AS variant_id,
                           BOOL_OR(COALESCE(holdout, false)) AS holdout,
                           MIN(experiment_scope) AS experiment_scope,
                           MIN(workflow_id) AS workflow_id,
                           MIN(workflow_version) AS workflow_version,
                           MIN(workflow_run_id) AS workflow_run_id,
                           MIN(step_id) AS step_id,
                           MIN(path_id) AS path_id,
                           MIN(goal_id) AS goal_id,
                           MIN(link_url) AS link_url,
                           MIN("timestamp") AS "timestamp",
                           MIN(metadata::text)::jsonb AS metadata
                    FROM raw_events
                    WHERE %s
                    GROUP BY tenant_id, workspace_id, event_type, id
                ) AS canonical_raw_events
                """.formatted(whereClause.strip());
    }

    private Map<String, Object> taxonomy(String eventType,
                                         String category,
                                         String description,
                                         List<String> requiredFields,
                                         List<String> metadataKeys) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("eventType", eventType);
        entry.put("category", category);
        entry.put("description", description);
        entry.put("requiredFields", requiredFields);
        entry.put("metadataKeys", metadataKeys);
        return entry;
    }

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String raw = String.valueOf(value);
        if (raw.contains(",") || raw.contains("\"") || raw.contains("\n")) {
            return "\"" + raw.replace("\"", "\"\"") + "\"";
        }
        return raw;
    }

    private int rollupBucketLimit(Integer requestedLimit, String normalizedGrain) {
        if ("day".equals(normalizedGrain)) {
            return boundedLimit(requestedLimit, DEFAULT_DAY_ROLLUP_BUCKET_LIMIT, MAX_DAY_ROLLUP_BUCKET_LIMIT);
        }
        return boundedLimit(requestedLimit, DEFAULT_HOUR_ROLLUP_BUCKET_LIMIT, MAX_HOUR_ROLLUP_BUCKET_LIMIT);
    }

    private Duration rollupWindowDuration(String normalizedGrain, int bucketLimit) {
        if ("day".equals(normalizedGrain)) {
            return Duration.ofDays(bucketLimit);
        }
        return Duration.ofHours(bucketLimit);
    }

    private int boundedLimit(Integer requestedLimit, int defaultLimit, int maxLimit) {
        if (requestedLimit == null || requestedLimit < 1) {
            return defaultLimit;
        }
        return Math.min(requestedLimit, maxLimit);
    }

    private TimeWindow boundedTimeWindow(Instant startAt, Instant endAt, Duration defaultWindow) {
        return boundedTimeWindow(startAt, endAt, defaultWindow, null);
    }

    private TimeWindow boundedTimeWindow(Instant startAt, Instant endAt, Duration defaultWindow, Duration maxWindow) {
        Instant effectiveEndAt = endAt == null ? Instant.now() : endAt;
        Instant effectiveStartAt = startAt == null ? effectiveEndAt.minus(defaultWindow) : startAt;
        if (effectiveStartAt.isAfter(effectiveEndAt)) {
            return null;
        }
        if (maxWindow != null && Duration.between(effectiveStartAt, effectiveEndAt).compareTo(maxWindow) > 0) {
            effectiveStartAt = effectiveEndAt.minus(maxWindow);
        }
        return new TimeWindow(effectiveStartAt, effectiveEndAt);
    }

    private record TimeWindow(Instant startAt, Instant endAt) {
    }
}

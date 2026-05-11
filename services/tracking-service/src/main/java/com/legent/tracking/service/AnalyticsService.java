package com.legent.tracking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getEventCounts(String tenantId, String workspaceId) {
        if (tenantId == null || tenantId.isBlank() || workspaceId == null || workspaceId.isBlank()) {
            log.warn("Tenant/workspace context missing; returning empty event counts");
            return new ArrayList<>();
        }
        try {
            return jdbcTemplate.queryForList("""
                SELECT event_type, COALESCE(count(*), 0) AS count
                FROM raw_events
                WHERE tenant_id = ? AND workspace_id = ?
                GROUP BY event_type
            """, tenantId, workspaceId);
        } catch (DataAccessException e) {
            log.error("Failed to query event counts for tenant {} workspace {}", tenantId, workspaceId, e);
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> getEventTimeline(String tenantId, String workspaceId, String eventType) {
        if (tenantId == null || tenantId.isBlank()
                || workspaceId == null || workspaceId.isBlank()
                || eventType == null || eventType.isBlank()) {
            log.warn("Tenant/workspace/eventType missing; returning empty timeline");
            return new ArrayList<>();
        }
        String normalizedEventType = eventType.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return jdbcTemplate.queryForList("""
                SELECT date_trunc('hour', "timestamp") AS hour, COALESCE(count(*), 0) AS count
                FROM raw_events
                WHERE tenant_id = ? AND workspace_id = ? AND event_type = ?
                GROUP BY hour
                ORDER BY hour
            """, tenantId, workspaceId, normalizedEventType);
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
                FROM raw_events
                WHERE tenant_id = ?
                  AND workspace_id = ?
                  AND campaign_id = ?
                  AND experiment_id = ?
                GROUP BY COALESCE(variant_id, 'HOLDOUT')
                ORDER BY variant_id
            """, tenantId, workspaceId, campaignId, experimentId);
        } catch (DataAccessException e) {
            log.error("Failed to query experiment metrics for tenant {} workspace {} campaign {} experiment {}",
                    tenantId, workspaceId, campaignId, experimentId, e);
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> getRollups(String tenantId, String workspaceId, String campaignId, String grain) {
        if (tenantId == null || tenantId.isBlank() || workspaceId == null || workspaceId.isBlank()) {
            return new ArrayList<>();
        }
        String normalizedGrain = "day".equalsIgnoreCase(grain) ? "day" : "hour";
        List<Object> params = new ArrayList<>(List.of(tenantId, workspaceId));
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
                FROM raw_events
                WHERE tenant_id = ? AND workspace_id = ?
                """.formatted(normalizedGrain));
        if (campaignId != null && !campaignId.isBlank()) {
            sql.append(" AND campaign_id = ?");
            params.add(campaignId);
        }
        sql.append(" GROUP BY campaign_id, bucket ORDER BY bucket DESC, campaign_id");
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
                       holdout, link_url, "timestamp", metadata
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
                "experiment_id", "variant_id", "holdout", "link_url", "timestamp", "metadata");
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
                taxonomy("CONVERSION", "business", "Tracked conversion event.", List.of("eventName"), List.of("value", "currency"))
        );
    }

    public Map<String, Object> rawCountsForCampaign(String tenantId, String workspaceId, String campaignId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT event_type, COUNT(*) AS count
                FROM raw_events
                WHERE tenant_id = ? AND workspace_id = ? AND campaign_id = ?
                GROUP BY event_type
                """, tenantId, workspaceId, campaignId);
        Map<String, Object> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            counts.put(String.valueOf(row.get("event_type")), row.get("count"));
        }
        return counts;
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
}

package com.legent.tracking.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ClickHouseRollupService {

    private final ObjectProvider<JdbcTemplate> clickHouseJdbcTemplateProvider;

    public ClickHouseRollupService(@Qualifier("clickHouseJdbcTemplate") ObjectProvider<JdbcTemplate> clickHouseJdbcTemplateProvider) {
        this.clickHouseJdbcTemplateProvider = clickHouseJdbcTemplateProvider;
    }

    public Map<String, Object> ensureRollupSchema() {
        List<String> statements = analyticsSchemaStatements();
        JdbcTemplate jdbc = clickHouseJdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            return Map.of("status", "SKIPPED", "reason", "ClickHouse JdbcTemplate not configured", "statements", statements);
        }
        for (String statement : statements) {
            jdbc.execute(statement);
        }
        return Map.of("status", "READY", "statementsApplied", statements.size(), "updatedAt", Instant.now());
    }

    public Map<String, Object> refreshCampaignDayRollups(String tenantId, String workspaceId, Instant from, Instant to) {
        JdbcTemplate jdbc = clickHouseJdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            return Map.of("status", "SKIPPED", "reason", "ClickHouse JdbcTemplate not configured");
        }
        Instant safeTo = to == null ? Instant.now().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS) : to;
        Instant safeFrom = from == null ? safeTo.minus(30, ChronoUnit.DAYS) : from;
        String sql = campaignDayRefreshSql();
        try {
            int rows = jdbc.update(sql, tenantId, workspaceId, Timestamp.from(safeFrom), Timestamp.from(safeTo));
            return Map.of(
                    "status", "REFRESHED",
                    "rowsWritten", rows,
                    "from", safeFrom,
                    "to", safeTo,
                    "updatedAt", Instant.now());
        } catch (DataAccessException e) {
            log.error("ClickHouse campaign day rollup refresh failed for tenant {} workspace {}", tenantId, workspaceId, e);
            return Map.of("status", "FAILED", "error", e.getMostSpecificCause().getMessage());
        }
    }

    public List<Map<String, Object>> campaignPerformance(String tenantId, String workspaceId, Instant from, Instant to, int limit) {
        JdbcTemplate jdbc = clickHouseJdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) {
            return new ArrayList<>();
        }
        Instant safeTo = to == null ? Instant.now() : to;
        Instant safeFrom = from == null ? safeTo.minus(30, ChronoUnit.DAYS) : from;
        int cappedLimit = Math.max(1, Math.min(limit, 1000));
        try {
            return jdbc.queryForList("""
                    SELECT campaign_id,
                           min(bucket_date) AS first_bucket,
                           max(bucket_date) AS last_bucket,
                           sum(sends) AS sends,
                           sum(delivered) AS delivered,
                           sum(opens) AS opens,
                           sum(clicks) AS clicks,
                           sum(bounces) AS bounces,
                           sum(complaints) AS complaints,
                           sum(unsubscribes) AS unsubscribes,
                           sum(conversions) AS conversions,
                           sum(revenue) AS revenue,
                           if(sum(delivered) = 0, 0, sum(opens) / sum(delivered)) AS open_rate,
                           if(sum(opens) = 0, 0, sum(clicks) / sum(opens)) AS click_to_open_rate,
                           if(sum(sends) = 0, 0, sum(bounces) / sum(sends)) AS bounce_rate,
                           if(sum(sends) = 0, 0, sum(complaints) / sum(sends)) AS complaint_rate
                    FROM campaign_day_rollups
                    WHERE tenant_id = ?
                      AND workspace_id = ?
                      AND bucket_date >= toDate(?)
                      AND bucket_date < toDate(?)
                    GROUP BY campaign_id
                    ORDER BY sends DESC
                    LIMIT ?
                    """, tenantId, workspaceId, Timestamp.from(safeFrom), Timestamp.from(safeTo), cappedLimit);
        } catch (DataAccessException e) {
            log.error("ClickHouse campaign performance query failed for tenant {} workspace {}", tenantId, workspaceId, e);
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> datasets() {
        List<Map<String, Object>> datasets = new ArrayList<>();
        datasets.add(dataset(
                "campaign_day_rollups",
                "BI-grade campaign performance by day",
                List.of("tenant_id", "workspace_id", "campaign_id", "bucket_date"),
                List.of("sends", "delivered", "opens", "clicks", "bounces", "complaints", "unsubscribes", "conversions", "revenue")));
        datasets.add(dataset(
                "raw_events",
                "Raw immutable tracking stream in ClickHouse",
                List.of("tenant_id", "workspace_id", "event_type", "campaign_id", "message_id"),
                List.of("timestamp", "metadata")));
        return datasets;
    }

    List<String> rollupSchemaStatements() {
        return List.of("""
                CREATE TABLE IF NOT EXISTS campaign_day_rollups (
                    tenant_id String,
                    workspace_id String,
                    campaign_id String,
                    bucket_date Date,
                    sends UInt64,
                    delivered UInt64,
                    opens UInt64,
                    clicks UInt64,
                    bounces UInt64,
                    complaints UInt64,
                    unsubscribes UInt64,
                    conversions UInt64,
                    revenue Float64,
                    unique_subscribers UInt64,
                    updated_at DateTime DEFAULT now()
                )
                ENGINE = SummingMergeTree()
                PARTITION BY toYYYYMM(bucket_date)
                ORDER BY (tenant_id, workspace_id, campaign_id, bucket_date)
                """);
    }

    List<String> rawEventSchemaStatements() {
        return List.of("""
                CREATE TABLE IF NOT EXISTS raw_events (
                    id String,
                    tenant_id String,
                    workspace_id String,
                    event_type LowCardinality(String),
                    campaign_id Nullable(String),
                    subscriber_id Nullable(String),
                    message_id Nullable(String),
                    user_agent Nullable(String),
                    ip_address Nullable(String),
                    link_url Nullable(String),
                    timestamp DateTime64(3, 'UTC'),
                    metadata String
                )
                ENGINE = MergeTree()
                PARTITION BY toYYYYMM(timestamp)
                ORDER BY (tenant_id, workspace_id, event_type, timestamp, id)
                TTL timestamp + INTERVAL 180 DAY DELETE
                """);
    }

    List<String> analyticsSchemaStatements() {
        List<String> statements = new ArrayList<>();
        statements.addAll(rawEventSchemaStatements());
        statements.addAll(rollupSchemaStatements());
        return statements;
    }

    String campaignDayRefreshSql() {
        return """
                INSERT INTO campaign_day_rollups
                SELECT tenant_id,
                       workspace_id,
                       ifNull(campaign_id, '') AS campaign_id,
                       toDate(timestamp) AS bucket_date,
                       countIf(event_type = 'SEND') AS sends,
                       countIf(event_type = 'DELIVERED') AS delivered,
                       countIf(event_type = 'OPEN') AS opens,
                       countIf(event_type = 'CLICK') AS clicks,
                       countIf(event_type = 'BOUNCE') AS bounces,
                       countIf(event_type = 'COMPLAINT') AS complaints,
                       countIf(event_type = 'UNSUBSCRIBE') AS unsubscribes,
                       countIf(event_type = 'CONVERSION') AS conversions,
                       sumIf(toFloat64OrZero(JSONExtractString(metadata, 'value')), event_type = 'CONVERSION') AS revenue,
                       uniqExact(subscriber_id) AS unique_subscribers,
                       now() AS updated_at
                FROM raw_events
                WHERE tenant_id = ?
                  AND workspace_id = ?
                  AND timestamp >= ?
                  AND timestamp < ?
                GROUP BY tenant_id, workspace_id, campaign_id, bucket_date
                """;
    }

    private Map<String, Object> dataset(String name, String description, List<String> dimensions, List<String> measures) {
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("name", name);
        dataset.put("description", description);
        dataset.put("dimensions", dimensions);
        dataset.put("measures", measures);
        return dataset;
    }
}

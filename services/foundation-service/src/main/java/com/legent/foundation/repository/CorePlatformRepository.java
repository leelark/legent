package com.legent.foundation.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class CorePlatformRepository {

    private static final Set<String> ALLOWED_DYNAMIC_TABLES = Set.of(
            "admin_sync_events",
            "ai_copilot_recommendations",
            "autonomous_optimization_policies",
            "autonomous_optimization_recommendations",
            "autonomous_optimization_rollbacks",
            "business_units",
            "compliance_export_jobs",
            "consent_ledger",
            "core_audit_events",
            "core_invitations",
            "delegated_access_grants",
            "departments",
            "developer_app_packages",
            "developer_sandboxes",
            "environment_locks",
            "environments",
            "extension_governance_packages",
            "extension_governance_test_runs",
            "feature_controls",
            "global_failover_drills",
            "global_operating_models",
            "governance_data_lineage_edges",
            "governance_evidence_packs",
            "governance_legal_holds",
            "governance_policy_simulation_runs",
            "immutable_audit_evidence",
            "marketplace_connector_instances",
            "marketplace_sync_jobs",
            "membership_links",
            "omnichannel_orchestration_flows",
            "operations_assistance_reviews",
            "organizations",
            "performance_optimization_policies",
            "performance_optimization_runs",
            "permission_groups",
            "personalization_evaluation_runs",
            "privacy_requests",
            "principal_role_bindings",
            "promotion_requests",
            "quota_policies",
            "realtime_decision_events",
            "realtime_decision_policies",
            "retention_matrix",
            "role_definitions",
            "scim_tokens",
            "slo_incident_automation_events",
            "slo_operations_policies",
            "teams",
            "tenant_bootstrap_status",
            "tenant_data_residency_policies",
            "tenant_encryption_policies",
            "usage_counters",
            "webhook_replay_jobs",
            "workflow_benchmark_runs",
            "workspaces"
    );

    private static final Set<String> ALLOWED_DYNAMIC_KEY_COLUMNS = Set.of(
            "app_key",
            "flow_key",
            "hold_key",
            "instance_key",
            "model_key",
            "package_key",
            "policy_key",
            "service_name"
    );

    private static final Set<String> ALLOWED_ORDER_DIRECTIONS = Set.of("ASC", "DESC");
    private static final Set<String> ALLOWED_NULLS_ORDER = Set.of("NULLS FIRST", "NULLS LAST");

    private final NamedParameterJdbcTemplate jdbc;

    public CorePlatformRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> insert(String table, Map<String, Object> values, List<String> jsonColumns) {
        String safeTable = safeTable(table);
        List<String> columns = new ArrayList<>(values.keySet());
        columns.forEach(CorePlatformRepository::safeColumn);
        jsonColumns.forEach(CorePlatformRepository::safeColumn);

        String sql = "INSERT INTO " + safeTable + " (" +
                String.join(", ", columns) +
                ") VALUES (" +
                columns.stream()
                        .map(column -> jsonColumns.contains(column)
                                ? "CAST(:" + column + " AS jsonb)"
                                : ":" + column)
                        .collect(Collectors.joining(", ")) +
                ") RETURNING *";

        return jdbc.queryForMap(sql, toSqlParams(values));
    }

    public List<Map<String, Object>> listByTenant(String table, String tenantId, String orderBy) {
        String sql = "SELECT * FROM " + safeTable(table) + " WHERE tenant_id = :tenantId AND deleted_at IS NULL ORDER BY " + safeOrderBy(orderBy);
        return jdbc.queryForList(sql, Map.of("tenantId", tenantId));
    }

    public List<Map<String, Object>> listWithFilters(
            String table,
            Map<String, Object> filters,
            String orderBy,
            Integer limit) {
        String safeTable = safeTable(table);
        filters.keySet().forEach(CorePlatformRepository::safeColumn);
        Map<String, Object> params = new LinkedHashMap<>(filters);
        String where = filters.keySet().stream()
                .map(key -> "(:" + key + " IS NULL OR " + safeColumn(key) + " = :" + key + ")")
                .collect(Collectors.joining(" AND "));

        StringBuilder sql = new StringBuilder("SELECT * FROM ")
                .append(safeTable)
                .append(" WHERE deleted_at IS NULL");

        if (!where.isBlank()) {
            sql.append(" AND ").append(where);
        }
        if (orderBy != null && !orderBy.isBlank()) {
            sql.append(" ORDER BY ").append(safeOrderBy(orderBy));
        }
        if (limit != null && limit > 0) {
            sql.append(" LIMIT :limit");
            params.put("limit", limit);
        }

        return jdbc.queryForList(sql.toString(), toSqlParamMap(params));
    }

    public Map<String, Object> updateById(
            String table,
            String id,
            String tenantId,
            Map<String, Object> updates,
            List<String> jsonColumns) {
        String safeTable = safeTable(table);
        if (updates.isEmpty()) {
            throw new IllegalArgumentException("At least one update field is required");
        }
        updates.keySet().forEach(CorePlatformRepository::safeColumn);
        jsonColumns.forEach(CorePlatformRepository::safeColumn);

        Map<String, Object> params = new LinkedHashMap<>(updates);
        params.put("id", id);
        params.put("tenantId", tenantId);

        String setClause = updates.keySet().stream()
                .map(key -> jsonColumns.contains(key)
                        ? safeColumn(key) + " = CAST(:" + key + " AS jsonb)"
                        : safeColumn(key) + " = :" + key)
                .collect(Collectors.joining(", "));

        String sql = "UPDATE " + safeTable +
                " SET " + setClause + ", updated_at = NOW(), version = version + 1 " +
                "WHERE id = :id AND tenant_id = :tenantId AND deleted_at IS NULL RETURNING *";

        return jdbc.queryForMap(sql, toSqlParams(params));
    }

    public Map<String, Object> updateByIdAndWorkspace(
            String table,
            String id,
            String tenantId,
            String workspaceId,
            Map<String, Object> updates,
            List<String> jsonColumns) {
        String safeTable = safeTable(table);
        if (updates.isEmpty()) {
            throw new IllegalArgumentException("At least one update field is required");
        }
        updates.keySet().forEach(CorePlatformRepository::safeColumn);
        jsonColumns.forEach(CorePlatformRepository::safeColumn);

        Map<String, Object> params = new LinkedHashMap<>(updates);
        params.put("id", id);
        params.put("tenantId", tenantId);
        params.put("workspaceId", workspaceId);

        String setClause = updates.keySet().stream()
                .map(key -> jsonColumns.contains(key)
                        ? safeColumn(key) + " = CAST(:" + key + " AS jsonb)"
                        : safeColumn(key) + " = :" + key)
                .collect(Collectors.joining(", "));

        String sql = "UPDATE " + safeTable +
                " SET " + setClause + ", updated_at = NOW(), version = version + 1 " +
                "WHERE id = :id AND tenant_id = :tenantId AND workspace_id = :workspaceId AND deleted_at IS NULL RETURNING *";

        return jdbc.queryForMap(sql, toSqlParams(params));
    }

    public Map<String, Object> queryForMap(String sql, Map<String, Object> params) {
        return jdbc.queryForMap(sql, toSqlParams(params));
    }

    public List<Map<String, Object>> queryForList(String sql, Map<String, Object> params) {
        return jdbc.queryForList(sql, toSqlParamMap(params));
    }

    public static String safeTable(String table) {
        String value = safeColumn(table);
        if (!ALLOWED_DYNAMIC_TABLES.contains(value)) {
            throw new IllegalArgumentException("Unsupported dynamic SQL table: " + table);
        }
        return value;
    }

    public static String safeKeyColumn(String column) {
        String value = safeColumn(column);
        if (!ALLOWED_DYNAMIC_KEY_COLUMNS.contains(value)) {
            throw new IllegalArgumentException("Unsupported dynamic SQL key column: " + column);
        }
        return value;
    }

    public static String safeOrderBy(String orderBy) {
        if (orderBy == null || orderBy.isBlank()) {
            throw new IllegalArgumentException("ORDER BY clause is required");
        }
        return Arrays.stream(orderBy.split(","))
                .map(String::trim)
                .map(CorePlatformRepository::safeOrderTerm)
                .collect(Collectors.joining(", "));
    }

    public static String safeColumn(String column) {
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("SQL identifier is required");
        }
        String value = column.trim();
        if (!value.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Unsafe SQL identifier: " + column);
        }
        return value;
    }

    private static String safeOrderTerm(String term) {
        String[] parts = term.split("\\s+");
        if (parts.length == 0 || parts.length > 4) {
            throw new IllegalArgumentException("Unsafe ORDER BY term: " + term);
        }

        String column = safeColumn(parts[0]);
        String direction = "";
        String nulls = "";
        int index = 1;
        if (index < parts.length && ALLOWED_ORDER_DIRECTIONS.contains(parts[index].toUpperCase(Locale.ROOT))) {
            direction = " " + parts[index].toUpperCase(Locale.ROOT);
            index++;
        }
        if (index < parts.length) {
            String remaining = String.join(" ", new LinkedHashSet<>(List.of(Arrays.copyOfRange(parts, index, parts.length)))).toUpperCase(Locale.ROOT);
            if (!ALLOWED_NULLS_ORDER.contains(remaining)) {
                throw new IllegalArgumentException("Unsafe ORDER BY nulls ordering: " + term);
            }
            nulls = " " + remaining;
        }
        return column + direction + nulls;
    }

    private MapSqlParameterSource toSqlParams(Map<String, Object> params) {
        MapSqlParameterSource source = new MapSqlParameterSource();
        params.forEach((key, value) -> source.addValue(key, toSqlValue(value)));
        return source;
    }

    private Map<String, Object> toSqlParamMap(Map<String, Object> params) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        params.forEach((key, value) -> mapped.put(key, toSqlValue(value)));
        return mapped;
    }

    private Object toSqlValue(Object value) {
        if (value instanceof Instant instant) {
            return Timestamp.from(instant);
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return Timestamp.from(offsetDateTime.toInstant());
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return Timestamp.from(zonedDateTime.toInstant());
        }
        if (value instanceof LocalDateTime localDateTime) {
            return Timestamp.valueOf(localDateTime);
        }
        return value;
    }
}

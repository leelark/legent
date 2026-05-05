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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class CorePlatformRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CorePlatformRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> insert(String table, Map<String, Object> values, List<String> jsonColumns) {
        List<String> columns = new ArrayList<>(values.keySet());

        String sql = "INSERT INTO " + table + " (" +
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
        String sql = "SELECT * FROM " + table + " WHERE tenant_id = :tenantId AND deleted_at IS NULL ORDER BY " + orderBy;
        return jdbc.queryForList(sql, Map.of("tenantId", tenantId));
    }

    public List<Map<String, Object>> listWithFilters(
            String table,
            Map<String, Object> filters,
            String orderBy,
            Integer limit) {
        Map<String, Object> params = new LinkedHashMap<>(filters);
        String where = filters.keySet().stream()
                .map(key -> ":" + key + " IS NULL OR " + key + " = :" + key)
                .collect(Collectors.joining(" AND "));

        StringBuilder sql = new StringBuilder("SELECT * FROM ")
                .append(table)
                .append(" WHERE deleted_at IS NULL");

        if (!where.isBlank()) {
            sql.append(" AND ").append(where);
        }
        if (orderBy != null && !orderBy.isBlank()) {
            sql.append(" ORDER BY ").append(orderBy);
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
        if (updates.isEmpty()) {
            throw new IllegalArgumentException("At least one update field is required");
        }

        Map<String, Object> params = new LinkedHashMap<>(updates);
        params.put("id", id);
        params.put("tenantId", tenantId);

        String setClause = updates.keySet().stream()
                .map(key -> jsonColumns.contains(key)
                        ? key + " = CAST(:" + key + " AS jsonb)"
                        : key + " = :" + key)
                .collect(Collectors.joining(", "));

        String sql = "UPDATE " + table +
                " SET " + setClause + ", updated_at = NOW(), version = version + 1 " +
                "WHERE id = :id AND tenant_id = :tenantId AND deleted_at IS NULL RETURNING *";

        return jdbc.queryForMap(sql, toSqlParams(params));
    }

    public Map<String, Object> queryForMap(String sql, Map<String, Object> params) {
        return jdbc.queryForMap(sql, toSqlParams(params));
    }

    public List<Map<String, Object>> queryForList(String sql, Map<String, Object> params) {
        return jdbc.queryForList(sql, toSqlParamMap(params));
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

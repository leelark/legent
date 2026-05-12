package com.legent.identity.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class FederationJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public FederationJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> insert(String table, Map<String, Object> values, List<String> jsonColumns) {
        List<String> columns = new ArrayList<>(values.keySet());
        String sql = "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (" +
                columns.stream()
                        .map(column -> jsonColumns.contains(column) ? "CAST(:" + column + " AS jsonb)" : ":" + column)
                        .collect(Collectors.joining(", ")) +
                ") RETURNING *";
        return jdbc.queryForMap(sql, params(values));
    }

    public Map<String, Object> updateById(String table, String id, String tenantId, Map<String, Object> updates, List<String> jsonColumns) {
        Map<String, Object> values = new LinkedHashMap<>(updates);
        values.put("id", id);
        values.put("tenantId", tenantId);
        String setClause = updates.keySet().stream()
                .map(column -> jsonColumns.contains(column) ? column + " = CAST(:" + column + " AS jsonb)" : column + " = :" + column)
                .collect(Collectors.joining(", "));
        String sql = "UPDATE " + table + " SET " + setClause + ", updated_at = NOW(), version = version + 1 " +
                "WHERE id = :id AND tenant_id = :tenantId AND deleted_at IS NULL RETURNING *";
        return jdbc.queryForMap(sql, params(values));
    }

    public List<Map<String, Object>> queryForList(String sql, Map<String, Object> values) {
        return jdbc.queryForList(sql, paramMap(values));
    }

    public Map<String, Object> queryForMap(String sql, Map<String, Object> values) {
        return jdbc.queryForMap(sql, params(values));
    }

    private MapSqlParameterSource params(Map<String, Object> values) {
        MapSqlParameterSource source = new MapSqlParameterSource();
        values.forEach((key, value) -> source.addValue(key, sqlValue(value)));
        return source;
    }

    private Map<String, Object> paramMap(Map<String, Object> values) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        values.forEach((key, value) -> mapped.put(key, sqlValue(value)));
        return mapped;
    }

    private Object sqlValue(Object value) {
        if (value instanceof Instant instant) {
            return Timestamp.from(instant);
        }
        return value;
    }
}

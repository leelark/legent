package com.legent.foundation.service.performance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.util.IdGenerator;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.security.TenantContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class PerformanceLedgerSupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    protected final CorePlatformRepository repository;
    protected final ObjectMapper objectMapper;

    protected PerformanceLedgerSupport(CorePlatformRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    protected Map<String, Object> requireById(String table, String id) {
        List<Map<String, Object>> rows = repository.queryForList(
                "SELECT * FROM " + table + " WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL LIMIT 1",
                map("tenantId", tenant(), "id", id)
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(table + " not found: " + id);
        }
        return rows.get(0);
    }

    protected Map<String, Object> upsertByKey(String table,
                                              String keyColumn,
                                              String keyValue,
                                              String workspaceId,
                                              Map<String, Object> values,
                                              List<String> jsonColumns) {
        List<Map<String, Object>> existing = repository.queryForList(
                "SELECT id FROM " + table + " WHERE tenant_id = :tenantId AND COALESCE(workspace_id, '') = COALESCE(:workspaceId, '') AND " + keyColumn + " = :keyValue AND deleted_at IS NULL LIMIT 1",
                map("tenantId", tenant(), "workspaceId", workspaceId, "keyValue", keyValue)
        );
        if (existing.isEmpty()) {
            return repository.insert(table, values, jsonColumns);
        }
        Map<String, Object> updates = new LinkedHashMap<>(values);
        updates.keySet().removeAll(List.of("id", "tenant_id", "workspace_id", "created_at", "created_by", "deleted_at", "version"));
        return repository.updateById(table, String.valueOf(existing.get(0).get("id")), tenant(), updates, jsonColumns);
    }

    protected List<Map<String, Object>> listScoped(String table, String workspaceId, String orderBy) {
        return repository.queryForList(
                "SELECT * FROM " + table + " WHERE tenant_id = :tenantId AND (:workspaceId IS NULL OR workspace_id = :workspaceId) AND deleted_at IS NULL ORDER BY " + orderBy,
                map("tenantId", tenant(), "workspaceId", workspaceId)
        );
    }

    protected List<Map<String, Object>> listLatest(String table, String workspaceId, int limit) {
        return repository.queryForList(
                "SELECT * FROM " + table + " WHERE tenant_id = :tenantId AND (:workspaceId IS NULL OR workspace_id = :workspaceId) AND deleted_at IS NULL ORDER BY created_at DESC LIMIT :limit",
                map("tenantId", tenant(), "workspaceId", workspaceId, "limit", limit)
        );
    }

    protected Map<String, Object> baseValues(String workspaceId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", IdGenerator.newId());
        values.put("tenant_id", tenant());
        values.put("workspace_id", workspaceId);
        values.put("created_at", Instant.now());
        values.put("updated_at", Instant.now());
        values.put("created_by", actor());
        values.put("deleted_at", null);
        values.put("version", 0L);
        return values;
    }

    protected Map<String, Object> readMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        try {
            return objectMapper.readValue(String.valueOf(value), MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    protected List<Map<String, Object>> readMapList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                result.add(readMap(item));
            }
            return result;
        }
        try {
            return objectMapper.readValue(String.valueOf(value), MAP_LIST_TYPE);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    protected List<String> readStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), STRING_LIST_TYPE);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    protected Map<String, Object> safeMapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    protected String toJson(Object value) {
        try {
            Object safe = value == null ? Map.of() : value;
            return objectMapper.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize performance intelligence payload", e);
        }
    }

    protected Map<String, Object> rec(String key, String priority, String detail) {
        return map("key", key, "priority", priority, "detail", detail);
    }

    protected Map<String, Object> finding(String key, String severity, String message) {
        return map("key", key, "severity", severity, "message", message);
    }

    protected boolean blockingFinding(Map<String, Object> finding) {
        String severity = normalize(asString(finding.get("severity")));
        return "BLOCK".equals(severity) || "BLOCKER".equals(severity) || "CRITICAL".equals(severity);
    }

    protected String riskBand(int risk) {
        if (risk >= 70) {
            return "HIGH";
        }
        if (risk >= 40) {
            return "MEDIUM";
        }
        return "LOW";
    }

    protected int valueOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    protected int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    protected double number(Object value, double fallback) {
        if (value instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    protected boolean bool(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    protected Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    protected Object firstObject(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    protected String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    protected String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    protected String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    protected String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    protected String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    protected String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    protected String tenant() {
        return TenantContext.requireTenantId();
    }

    protected String workspace(String workspaceId) {
        String resolved = blankToNull(workspaceId);
        return resolved == null ? TenantContext.getWorkspaceId() : resolved;
    }

    protected String actor() {
        String actor = TenantContext.getUserId();
        return actor == null || actor.isBlank() ? "SYSTEM" : actor;
    }

    protected Map<String, Object> map(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return values;
    }
}

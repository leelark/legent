package com.legent.foundation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.util.IdGenerator;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOperationsService {

    private final CorePlatformRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        String tenantId = requireTenant();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("generatedAt", Instant.now());
        response.put("health", health(tenantId));
        response.put("stats", stats(tenantId));
        response.put("modules", modules(tenantId));
        response.put("moduleStatuses", moduleStatuses(tenantId));
        response.put("jobs", jobs(tenantId));
        response.put("alerts", alerts(tenantId));
        response.put("recommendedActions", recommendedActions(tenantId));
        response.put("cacheState", cacheState());
        response.put("staleConfig", staleConfig(tenantId));
        response.put("activity", recentAudit(tenantId, 12));
        response.put("syncEvents", recentSync(tenantId, 12));
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> accessOverview() {
        String tenantId = requireTenant();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("roles", safeQueryForList("""
                SELECT role_key, display_name, description, is_system, permissions, updated_at
                FROM role_definitions
                WHERE deleted_at IS NULL AND (tenant_id IS NULL OR tenant_id = :tenantId)
                ORDER BY tenant_id NULLS FIRST, role_key ASC
                """, Map.of("tenantId", tenantId)));
        response.put("permissionGroups", safeQueryForList("""
                SELECT group_key, display_name, permissions, updated_at
                FROM permission_groups
                WHERE deleted_at IS NULL AND (tenant_id IS NULL OR tenant_id = :tenantId)
                ORDER BY tenant_id NULLS FIRST, group_key ASC
                """, Map.of("tenantId", tenantId)));
        response.put("memberships", safeQueryForList("""
                SELECT id, user_id, workspace_id, team_id, department_id, status, principal_type, updated_at
                FROM membership_links
                WHERE tenant_id = :tenantId AND deleted_at IS NULL
                ORDER BY updated_at DESC
                LIMIT 100
                """, Map.of("tenantId", tenantId)));
        response.put("delegatedAccess", safeQueryForList("""
                SELECT id, grantor_user_id, grantee_user_id, workspace_id, permissions, status, expires_at, updated_at
                FROM delegated_access_grants
                WHERE tenant_id = :tenantId AND deleted_at IS NULL
                ORDER BY updated_at DESC
                LIMIT 100
                """, Map.of("tenantId", tenantId)));
        response.put("propagation", recentSyncByTypes(tenantId, List.of(
                "ROLE_DEFINITION_CHANGED",
                "PERMISSION_GROUP_CHANGED",
                "ACCESS_GRANT_CHANGED",
                "MEMBERSHIP_CHANGED",
                "FEATURE_CONTROL_CHANGED"
        )));
        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> syncEvents(String status, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", requireTenant());
        params.put("limit", Math.max(1, Math.min(limit, 250)));

        StringBuilder sql = new StringBuilder("""
                SELECT *
                FROM admin_sync_events
                WHERE tenant_id = :tenantId AND deleted_at IS NULL
                """);

        String normalizedStatus = blankToNull(status);
        if (normalizedStatus != null) {
            sql.append(" AND status = :status");
            params.put("status", normalizedStatus.toUpperCase());
        }
        sql.append(" ORDER BY created_at DESC LIMIT :limit");
        return safeQueryForList(sql.toString(), params);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> recordSyncEvent(
            String eventType,
            String sourceModule,
            List<String> targetModules,
            Map<String, Object> payload) {
        String tenantId = requireTenant();
        String actor = currentActor();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", IdGenerator.newId());
        values.put("tenant_id", tenantId);
        values.put("workspace_id", TenantContext.getWorkspaceId());
        values.put("environment_id", TenantContext.getEnvironmentId());
        values.put("event_type", eventType);
        values.put("source_module", sourceModule);
        values.put("target_modules", toJson(targetModules == null ? List.of() : targetModules));
        values.put("payload", toJson(payload == null ? Map.of() : payload));
        values.put("status", "APPLIED");
        values.put("applied_at", Instant.now());
        values.put("failure_reason", null);
        values.put("created_at", Instant.now());
        values.put("updated_at", Instant.now());
        values.put("created_by", actor);
        values.put("deleted_at", null);
        values.put("version", 0L);
        Map<String, Object> saved = repository.insert("admin_sync_events", values, List.of("target_modules", "payload"));
        try {
            recordAudit("ADMIN_SYNC_EVENT", "AdminSyncEvent", saved.get("id"), saved);
        } catch (Exception ex) {
            log.warn("Admin sync event audit write skipped for eventType={}: {}", eventType, ex.getMessage());
        }
        return saved;
    }

    private Map<String, Object> health(String tenantId) {
        long failures = count("""
                SELECT COUNT(*) FROM core_audit_events
                WHERE tenant_id = :tenantId
                  AND deleted_at IS NULL
                  AND status <> 'SUCCESS'
                  AND created_at > NOW() - INTERVAL '24 hours'
                """, Map.of("tenantId", tenantId));
        long pendingSync = count("""
                SELECT COUNT(*) FROM admin_sync_events
                WHERE tenant_id = :tenantId
                  AND deleted_at IS NULL
                  AND status IN ('PENDING', 'FAILED')
                """, Map.of("tenantId", tenantId));
        long lockedEnvironments = count("""
                SELECT COUNT(*) FROM environment_locks
                WHERE tenant_id = :tenantId
                  AND deleted_at IS NULL
                  AND is_active = TRUE
                """, Map.of("tenantId", tenantId));

        String status = failures > 0 || pendingSync > 0 ? "DEGRADED" : "OPERATIONAL";
        if (lockedEnvironments > 0 && "OPERATIONAL".equals(status)) {
            status = "WATCH";
        }
        return Map.of(
                "status", status,
                "failedActions24h", failures,
                "pendingSyncEvents", pendingSync,
                "lockedEnvironments", lockedEnvironments,
                "pollSeconds", 15
        );
    }

    private Map<String, Object> stats(String tenantId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("workspaces", countActive("workspaces", tenantId));
        stats.put("memberships", countActive("membership_links", tenantId));
        stats.put("roles", countRoleDefinitions(tenantId));
        stats.put("featureControls", countActive("feature_controls", tenantId));
        stats.put("runtimeConfigs", count("""
                SELECT COUNT(*) FROM system_configs
                WHERE deleted_at IS NULL AND (tenant_id IS NULL OR tenant_id = :tenantId)
                """, Map.of("tenantId", tenantId)));
        stats.put("auditEvents24h", count("""
                SELECT COUNT(*) FROM core_audit_events
                WHERE tenant_id = :tenantId
                  AND deleted_at IS NULL
                  AND created_at > NOW() - INTERVAL '24 hours'
                """, Map.of("tenantId", tenantId)));
        stats.put("pendingAccess", count("""
                SELECT COUNT(*) FROM delegated_access_grants
                WHERE tenant_id = :tenantId
                  AND deleted_at IS NULL
                  AND status = 'PENDING'
                """, Map.of("tenantId", tenantId)));
        return stats;
    }

    private List<Map<String, Object>> modules(String tenantId) {
        List<Map<String, Object>> modules = new ArrayList<>();
        addModule(modules, tenantId, "audience", "Audience", "identity, consent, segment readiness");
        addModule(modules, tenantId, "template", "Template", "content governance and rendering");
        addModule(modules, tenantId, "campaign", "Campaign", "launch workflow and approvals");
        addModule(modules, tenantId, "automation", "Automation", "journey orchestration");
        addModule(modules, tenantId, "delivery", "Delivery", "providers, queues, retries");
        addModule(modules, tenantId, "analytics", "Analytics", "feedback and visibility");
        addModule(modules, tenantId, "security", "Security", "access policy and controls");
        return modules;
    }

    private void addModule(List<Map<String, Object>> modules, String tenantId, String key, String label, String description) {
        long configs = count("""
                SELECT COUNT(*) FROM system_configs
                WHERE deleted_at IS NULL
                  AND (tenant_id IS NULL OR tenant_id = :tenantId)
                  AND LOWER(module_key) = :moduleKey
                """, Map.of("tenantId", tenantId, "moduleKey", key));
        long audit = count("""
                SELECT COUNT(*) FROM core_audit_events
                WHERE tenant_id = :tenantId
                  AND deleted_at IS NULL
                  AND LOWER(resource_type) LIKE CONCAT('%', :moduleKey, '%')
                  AND created_at > NOW() - INTERVAL '7 days'
                """, Map.of("tenantId", tenantId, "moduleKey", key));
        long sync = count("""
                SELECT COUNT(*) FROM admin_sync_events
                WHERE tenant_id = :tenantId
                  AND deleted_at IS NULL
                  AND CAST(target_modules AS text) ILIKE CONCAT('%', :moduleKey, '%')
                  AND created_at > NOW() - INTERVAL '7 days'
                """, Map.of("tenantId", tenantId, "moduleKey", key));
        modules.add(Map.of(
                "key", key,
                "label", label,
                "description", description,
                "configs", configs,
                "auditEvents", audit,
                "syncEvents", sync,
                "status", "OPERATIONAL"
        ));
    }

    private List<Map<String, Object>> jobs(String tenantId) {
        List<Map<String, Object>> jobs = new ArrayList<>();
        jobs.add(Map.of(
                "name", "Tenant bootstrap",
                "status", latestValue("tenant_bootstrap_status", tenantId, "status", "PENDING"),
                "queued", count("""
                        SELECT COUNT(*) FROM tenant_bootstrap_status
                        WHERE tenant_id = :tenantId AND status IN ('PENDING', 'REPAIRING')
                        """, Map.of("tenantId", tenantId))
        ));
        jobs.add(Map.of(
                "name", "Environment promotions",
                "status", "TRACKED",
                "queued", count("""
                        SELECT COUNT(*) FROM promotion_requests
                        WHERE tenant_id = :tenantId AND deleted_at IS NULL AND status = 'PENDING'
                        """, Map.of("tenantId", tenantId))
        ));
        jobs.add(Map.of(
                "name", "Config propagation",
                "status", "APPLIED",
                "queued", count("""
                        SELECT COUNT(*) FROM admin_sync_events
                        WHERE tenant_id = :tenantId AND deleted_at IS NULL AND status = 'PENDING'
                        """, Map.of("tenantId", tenantId))
        ));
        return jobs;
    }

    private Map<String, Object> moduleStatuses(String tenantId) {
        Map<String, Object> statuses = new LinkedHashMap<>();
        for (Map<String, Object> module : modules(tenantId)) {
            String key = String.valueOf(module.get("key"));
            long failedSync = count("""
                    SELECT COUNT(*) FROM admin_sync_events
                    WHERE tenant_id = :tenantId
                      AND deleted_at IS NULL
                      AND status = 'FAILED'
                      AND CAST(target_modules AS text) ILIKE CONCAT('%', :moduleKey, '%')
                    """, Map.of("tenantId", tenantId, "moduleKey", key));
            long recentAudit = asLong(module.get("auditEvents"));
            statuses.put(key, Map.of(
                    "status", failedSync > 0 ? "DEGRADED" : "OPERATIONAL",
                    "failedSyncEvents", failedSync,
                    "auditEvents7d", recentAudit,
                    "lastCheckedAt", Instant.now()
            ));
        }
        return statuses;
    }

    private List<Map<String, Object>> recommendedActions(String tenantId) {
        List<Map<String, Object>> actions = new ArrayList<>();
        long failedSync = count("""
                SELECT COUNT(*) FROM admin_sync_events
                WHERE tenant_id = :tenantId AND deleted_at IS NULL AND status = 'FAILED'
                """, Map.of("tenantId", tenantId));
        long pendingAccess = count("""
                SELECT COUNT(*) FROM delegated_access_grants
                WHERE tenant_id = :tenantId AND deleted_at IS NULL AND status = 'PENDING'
                """, Map.of("tenantId", tenantId));
        long staleConfig = countStaleConfig(tenantId);
        if (failedSync > 0) {
            actions.add(Map.of(
                    "key", "sync.replay",
                    "tone", "danger",
                    "title", "Review failed propagation",
                    "detail", failedSync + " admin sync events failed and may leave modules stale.",
                    "action", "Open sync ledger"
            ));
        }
        if (pendingAccess > 0) {
            actions.add(Map.of(
                    "key", "access.review",
                    "tone", "warning",
                    "title", "Review pending access",
                    "detail", pendingAccess + " delegated access grants need an admin decision.",
                    "action", "Open role engine"
            ));
        }
        if (staleConfig > 0) {
            actions.add(Map.of(
                    "key", "config.review",
                    "tone", "info",
                    "title", "Review stale runtime config",
                    "detail", staleConfig + " settings have not changed in the last 90 days.",
                    "action", "Open configuration"
            ));
        }
        if (actions.isEmpty()) {
            actions.add(Map.of(
                    "key", "ops.clean",
                    "tone", "success",
                    "title", "Operations steady",
                    "detail", "No failed sync, pending access, or stale critical config detected.",
                    "action", "Monitor"
            ));
        }
        return actions;
    }

    private Map<String, Object> cacheState() {
        return Map.of(
                "settings", "TENANT_AWARE_EVICT_ON_APPLY",
                "publicContent", "TENANT_AWARE_EVICT_ON_PUBLISH",
                "featureFlags", "TENANT_AWARE_EVICT_ON_CHANGE",
                "checkedAt", Instant.now()
        );
    }

    private Map<String, Object> staleConfig(String tenantId) {
        long count = countStaleConfig(tenantId);
        return Map.of(
                "count", count,
                "status", count > 0 ? "REVIEW" : "CURRENT",
                "thresholdDays", 90
        );
    }

    private List<Map<String, Object>> alerts(String tenantId) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        long failed = count("""
                SELECT COUNT(*) FROM core_audit_events
                WHERE tenant_id = :tenantId AND deleted_at IS NULL AND status <> 'SUCCESS'
                """, Map.of("tenantId", tenantId));
        long pendingAccess = count("""
                SELECT COUNT(*) FROM delegated_access_grants
                WHERE tenant_id = :tenantId AND deleted_at IS NULL AND status = 'PENDING'
                """, Map.of("tenantId", tenantId));
        long failedSync = count("""
                SELECT COUNT(*) FROM admin_sync_events
                WHERE tenant_id = :tenantId AND deleted_at IS NULL AND status = 'FAILED'
                """, Map.of("tenantId", tenantId));
        if (failed > 0) {
            alerts.add(Map.of("tone", "danger", "title", "Failed governed actions", "detail", failed + " failed audit events need review"));
        }
        if (pendingAccess > 0) {
            alerts.add(Map.of("tone", "warning", "title", "Access approvals pending", "detail", pendingAccess + " delegated grants awaiting decision"));
        }
        if (failedSync > 0) {
            alerts.add(Map.of("tone", "danger", "title", "Propagation failures", "detail", failedSync + " sync events failed"));
        }
        if (alerts.isEmpty()) {
            alerts.add(Map.of("tone", "success", "title", "No critical alerts", "detail", "Config, access, and audit propagation clean"));
        }
        return alerts;
    }

    private List<Map<String, Object>> recentAudit(String tenantId, int limit) {
        return safeQueryForList("""
                SELECT action, resource_type, resource_id, actor_id, status, created_at
                FROM core_audit_events
                WHERE tenant_id = :tenantId AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT :limit
                """, Map.of("tenantId", tenantId, "limit", limit));
    }

    private List<Map<String, Object>> recentSync(String tenantId, int limit) {
        return safeQueryForList("""
                SELECT event_type, source_module, target_modules, status, applied_at, created_at
                FROM admin_sync_events
                WHERE tenant_id = :tenantId AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT :limit
                """, Map.of("tenantId", tenantId, "limit", limit));
    }

    private List<Map<String, Object>> recentSyncByTypes(String tenantId, List<String> eventTypes) {
        return safeQueryForList("""
                SELECT event_type, source_module, target_modules, status, applied_at, created_at
                FROM admin_sync_events
                WHERE tenant_id = :tenantId
                  AND deleted_at IS NULL
                  AND event_type IN (:eventTypes)
                ORDER BY created_at DESC
                LIMIT 50
                """, Map.of("tenantId", tenantId, "eventTypes", eventTypes));
    }

    private long countActive(String table, String tenantId) {
        return count("SELECT COUNT(*) FROM " + CorePlatformRepository.safeTable(table) + " WHERE tenant_id = :tenantId AND deleted_at IS NULL", Map.of("tenantId", tenantId));
    }

    private long countRoleDefinitions(String tenantId) {
        return count("""
                SELECT COUNT(*) FROM role_definitions
                WHERE deleted_at IS NULL AND (tenant_id IS NULL OR tenant_id = :tenantId)
                """, Map.of("tenantId", tenantId));
    }

    private long countStaleConfig(String tenantId) {
        return count("""
                SELECT COUNT(*) FROM system_configs
                WHERE deleted_at IS NULL
                  AND (tenant_id IS NULL OR tenant_id = :tenantId)
                  AND updated_at < NOW() - INTERVAL '90 days'
                """, Map.of("tenantId", tenantId));
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private long count(String sql, Map<String, Object> params) {
        try {
            Object value = repository.queryForMap(sql, params).values().iterator().next();
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(String.valueOf(value));
        } catch (DataAccessException | NumberFormatException ex) {
            log.warn("Admin count query skipped: {}", ex.getMessage());
            return 0L;
        }
    }

    private String latestValue(String table, String tenantId, String column, String fallback) {
        String safeColumn = CorePlatformRepository.safeColumn(column);
        List<Map<String, Object>> rows = safeQueryForList(
                "SELECT " + safeColumn + " FROM " + CorePlatformRepository.safeTable(table) + " WHERE tenant_id = :tenantId ORDER BY updated_at DESC LIMIT 1",
                Map.of("tenantId", tenantId)
        );
        if (rows.isEmpty()) {
            return fallback;
        }
        Object value = rows.get(0).get(safeColumn);
        return value == null ? fallback : String.valueOf(value);
    }

    private List<Map<String, Object>> safeQueryForList(String sql, Map<String, Object> params) {
        try {
            return repository.queryForList(sql, params);
        } catch (DataAccessException ex) {
            log.warn("Admin query skipped: {}", ex.getMessage());
            return List.of();
        }
    }

    private void recordAudit(String action, String resourceType, Object resourceId, Map<String, Object> details) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", IdGenerator.newId());
        values.put("tenant_id", requireTenant());
        values.put("workspace_id", TenantContext.getWorkspaceId());
        values.put("environment_id", TenantContext.getEnvironmentId());
        values.put("actor_id", currentActor());
        values.put("action", action);
        values.put("resource_type", resourceType);
        values.put("resource_id", resourceId == null ? null : resourceId.toString());
        values.put("ownership_scope", "TENANT");
        values.put("request_id", TenantContext.getRequestId());
        values.put("correlation_id", TenantContext.getCorrelationId());
        values.put("status", "SUCCESS");
        values.put("details", toJson(details));
        values.put("created_at", Instant.now());
        values.put("updated_at", Instant.now());
        values.put("created_by", currentActor());
        values.put("deleted_at", null);
        values.put("version", 0L);
        repository.insert("core_audit_events", values, List.of("details"));
    }

    private String requireTenant() {
        return TenantContext.requireTenantId();
    }

    private String currentActor() {
        String actor = TenantContext.getUserId();
        return actor == null || actor.isBlank() ? "SYSTEM" : actor;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize admin operation payload", e);
        }
    }
}

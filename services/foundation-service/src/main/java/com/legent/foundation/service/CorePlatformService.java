package com.legent.foundation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.util.IdGenerator;
import com.legent.foundation.dto.CorePlatformDto;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CorePlatformService {

    private final CorePlatformRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> createOrganization(CorePlatformDto.OrganizationRequest request) {
        String tenantId = requireTenant();
        String actorId = currentActor();

        String sql = """
                INSERT INTO organizations (id, tenant_id, name, slug, status, metadata, created_at, updated_at, created_by, version)
                VALUES (:id, :tenantId, :name, :slug, :status, CAST(:metadata AS jsonb), NOW(), NOW(), :createdBy, 0)
                ON CONFLICT (tenant_id) DO UPDATE
                SET name = EXCLUDED.name,
                    slug = EXCLUDED.slug,
                    status = EXCLUDED.status,
                    metadata = EXCLUDED.metadata,
                    updated_at = NOW(),
                    version = organizations.version + 1
                RETURNING *
                """;

        Map<String, Object> params = Map.of(
                "id", tenantId,
                "tenantId", tenantId,
                "name", request.getName(),
                "slug", request.getSlug(),
                "status", defaultValue(request.getStatus(), "ACTIVE"),
                "metadata", toJson(request.getMetadata()),
                "createdBy", actorId
        );
        Map<String, Object> saved = repository.queryForMap(sql, params);
        recordAudit("ORGANIZATION_UPSERT", "Organization", saved.get("id"), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOrganizations() {
        return repository.listByTenant("organizations", requireTenant(), "created_at DESC");
    }

    @Transactional
    public Map<String, Object> createBusinessUnit(CorePlatformDto.BusinessUnitRequest request) {
        assertExists("organizations", request.getOrganizationId(), "organizationId");
        Map<String, Object> values = baseValues(requireTenant());
        values.put("organization_id", request.getOrganizationId());
        values.put("parent_id", nullable(request.getParentId()));
        values.put("code", nullable(request.getCode()));
        values.put("name", request.getName());
        values.put("description", nullable(request.getDescription()));
        values.put("metadata", toJson(request.getMetadata()));
        Map<String, Object> saved = repository.insert("business_units", values, List.of("metadata"));
        recordAudit("BUSINESS_UNIT_CREATE", "BusinessUnit", saved.get("id"), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listBusinessUnits() {
        return repository.listByTenant("business_units", requireTenant(), "created_at DESC");
    }

    @Transactional
    public Map<String, Object> createWorkspace(CorePlatformDto.WorkspaceRequest request) {
        assertExists("organizations", request.getOrganizationId(), "organizationId");
        String businessUnitId = nullable(request.getBusinessUnitId());
        if (businessUnitId != null) {
            assertExists("business_units", businessUnitId, "businessUnitId");
        }
        Map<String, Object> values = baseValues(requireTenant());
        values.put("organization_id", request.getOrganizationId());
        values.put("business_unit_id", businessUnitId);
        values.put("name", request.getName());
        values.put("slug", request.getSlug());
        values.put("status", defaultValue(request.getStatus(), "ACTIVE"));
        values.put("default_environment", defaultValue(request.getDefaultEnvironment(), "PRODUCTION"));
        values.put("metadata", toJson(request.getMetadata()));
        Map<String, Object> saved = repository.insert("workspaces", values, List.of("metadata"));
        recordAudit("WORKSPACE_CREATE", "Workspace", saved.get("id"), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listWorkspaces() {
        return repository.listByTenant("workspaces", requireTenant(), "created_at DESC");
    }

    @Transactional
    public Map<String, Object> createTeam(CorePlatformDto.TeamRequest request) {
        assertExists("workspaces", request.getWorkspaceId(), "workspaceId");
        Map<String, Object> values = baseValues(requireTenant());
        values.put("workspace_id", request.getWorkspaceId());
        values.put("name", request.getName());
        values.put("code", nullable(request.getCode()));
        values.put("status", defaultValue(request.getStatus(), "ACTIVE"));
        values.put("metadata", toJson(request.getMetadata()));
        Map<String, Object> saved = repository.insert("teams", values, List.of("metadata"));
        recordAudit("TEAM_CREATE", "Team", saved.get("id"), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeams() {
        return repository.listByTenant("teams", requireTenant(), "created_at DESC");
    }

    @Transactional
    public Map<String, Object> createDepartment(CorePlatformDto.DepartmentRequest request) {
        assertExists("workspaces", request.getWorkspaceId(), "workspaceId");
        Map<String, Object> values = baseValues(requireTenant());
        values.put("workspace_id", request.getWorkspaceId());
        values.put("name", request.getName());
        values.put("code", nullable(request.getCode()));
        values.put("metadata", toJson(request.getMetadata()));
        Map<String, Object> saved = repository.insert("departments", values, List.of("metadata"));
        recordAudit("DEPARTMENT_CREATE", "Department", saved.get("id"), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDepartments() {
        return repository.listByTenant("departments", requireTenant(), "created_at DESC");
    }

    @Transactional
    public Map<String, Object> createMembership(CorePlatformDto.MembershipRequest request) {
        assertExists("organizations", request.getOrganizationId(), "organizationId");
        String businessUnitId = nullable(request.getBusinessUnitId());
        if (businessUnitId != null) {
            assertExists("business_units", businessUnitId, "businessUnitId");
        }
        String workspaceId = nullable(request.getWorkspaceId());
        if (workspaceId != null) {
            assertExists("workspaces", workspaceId, "workspaceId");
        }
        String teamId = nullable(request.getTeamId());
        if (teamId != null) {
            assertExists("teams", teamId, "teamId");
        }
        String departmentId = nullable(request.getDepartmentId());
        if (departmentId != null) {
            assertExists("departments", departmentId, "departmentId");
        }

        Map<String, Object> values = baseValues(requireTenant());
        values.put("user_id", request.getUserId());
        values.put("organization_id", request.getOrganizationId());
        values.put("business_unit_id", businessUnitId);
        values.put("workspace_id", workspaceId);
        values.put("team_id", teamId);
        values.put("department_id", departmentId);
        values.put("status", defaultValue(request.getStatus(), "ACTIVE"));
        values.put("principal_type", defaultValue(request.getPrincipalType(), "USER"));
        values.put("metadata", toJson(request.getMetadata()));
        Map<String, Object> saved = repository.insert("membership_links", values, List.of("metadata"));
        recordAudit("MEMBERSHIP_CREATE", "Membership", saved.get("id"), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMemberships() {
        return repository.listByTenant("membership_links", requireTenant(), "created_at DESC");
    }

    @Transactional
    public Map<String, Object> createRoleDefinition(CorePlatformDto.RoleDefinitionRequest request) {
        Map<String, Object> values = baseValues(requireTenant());
        values.put("tenant_id", nullable(request.getTenantId()) == null ? requireTenant() : request.getTenantId());
        values.put("role_key", request.getRoleKey());
        values.put("display_name", request.getDisplayName());
        values.put("description", nullable(request.getDescription()));
        values.put("is_system", request.getSystem() != null && request.getSystem());
        values.put("permissions", toJson(request.getPermissions()));
        values.put("metadata", toJson(request.getMetadata()));
        Map<String, Object> saved = repository.insert("role_definitions", values, List.of("permissions", "metadata"));
        recordAudit("ROLE_DEFINITION_CREATE", "RoleDefinition", saved.get("id"), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRoleDefinitions() {
        return repository.queryForList(
                "SELECT * FROM role_definitions WHERE deleted_at IS NULL AND (tenant_id IS NULL OR tenant_id = :tenantId) ORDER BY tenant_id NULLS FIRST, created_at DESC",
                Map.of("tenantId", requireTenant())
        );
    }

    @Transactional
    public Map<String, Object> createPermissionGroup(CorePlatformDto.PermissionGroupRequest request) {
        Map<String, Object> values = baseValues(requireTenant());
        values.put("tenant_id", nullable(request.getTenantId()) == null ? requireTenant() : request.getTenantId());
        values.put("group_key", request.getGroupKey());
        values.put("display_name", request.getDisplayName());
        values.put("permissions", toJson(request.getPermissions()));
        values.put("metadata", toJson(request.getMetadata()));
        Map<String, Object> saved = repository.insert("permission_groups", values, List.of("permissions", "metadata"));
        recordAudit("PERMISSION_GROUP_CREATE", "PermissionGroup", saved.get("id"), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPermissionGroups() {
        return repository.queryForList(
                "SELECT * FROM permission_groups WHERE deleted_at IS NULL AND (tenant_id IS NULL OR tenant_id = :tenantId) ORDER BY tenant_id NULLS FIRST, created_at DESC",
                Map.of("tenantId", requireTenant())
        );
    }

    @Transactional
    public Map<String, Object> createAccessGrant(CorePlatformDto.AccessGrantRequest request) {
        Map<String, Object> values = baseValues(requireTenant());
        values.put("grantor_user_id", currentActor());
        values.put("grantee_user_id", request.getGranteeUserId());
        values.put("workspace_id", nullable(request.getWorkspaceId()));
        values.put("permissions", toJson(request.getPermissions()));
        values.put("reason", nullable(request.getReason()));
        values.put("status", "PENDING");
        values.put("expires_at", request.getExpiresAt());
        values.put("approved_by", null);
        values.put("approved_at", null);
        Map<String, Object> saved = repository.insert("delegated_access_grants", values, List.of("permissions"));
        recordAudit("ACCESS_GRANT_CREATE", "DelegatedAccessGrant", saved.get("id"), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAccessGrants() {
        return repository.listByTenant("delegated_access_grants", requireTenant(), "created_at DESC");
    }

    @Transactional
    public Map<String, Object> createInvitation(CorePlatformDto.InvitationRequest request) {
        Map<String, Object> values = baseValues(requireTenant());
        values.put("email", request.getEmail().trim().toLowerCase());
        values.put("invited_by", currentActor());
        values.put("organization_id", nullable(request.getOrganizationId()));
        values.put("workspace_id", nullable(request.getWorkspaceId()));
        values.put("role_keys", toJson(request.getRoleKeys()));
        values.put("token", UUID.randomUUID().toString().replace("-", ""));
        values.put("status", "PENDING");
        values.put("expires_at", request.getExpiresAt());
        values.put("accepted_at", null);
        values.put("metadata", toJson(request.getMetadata()));
        Map<String, Object> saved = repository.insert("core_invitations", values, List.of("role_keys", "metadata"));
        recordAudit("INVITATION_CREATE", "CoreInvitation", saved.get("id"), saved);
        return saved;
    }

    @Transactional
    public Map<String, Object> acceptInvitation(CorePlatformDto.InvitationAcceptRequest request) {
        String sql = """
                UPDATE core_invitations
                SET status = 'ACCEPTED', accepted_at = NOW(), updated_at = NOW(), version = version + 1
                WHERE tenant_id = :tenantId AND token = :token AND deleted_at IS NULL
                RETURNING *
                """;
        Map<String, Object> saved = repository.queryForMap(sql, Map.of("tenantId", requireTenant(), "token", request.getToken()));
        recordAudit("INVITATION_ACCEPT", "CoreInvitation", saved.get("id"), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listInvitations() {
        return repository.listByTenant("core_invitations", requireTenant(), "created_at DESC");
    }

    @Transactional
    public Map<String, Object> upsertQuotaPolicy(CorePlatformDto.QuotaPolicyRequest request) {
        String sql = """
                INSERT INTO quota_policies (id, tenant_id, workspace_id, metric_key, soft_limit, hard_limit, overage_rate, is_enabled, metadata, created_at, updated_at, created_by, version)
                VALUES (:id, :tenantId, :workspaceId, :metricKey, :softLimit, :hardLimit, :overageRate, :isEnabled, CAST(:metadata AS jsonb), NOW(), NOW(), :createdBy, 0)
                ON CONFLICT (tenant_id, COALESCE(workspace_id, ''), metric_key)
                WHERE deleted_at IS NULL
                DO UPDATE SET
                    soft_limit = EXCLUDED.soft_limit,
                    hard_limit = EXCLUDED.hard_limit,
                    overage_rate = EXCLUDED.overage_rate,
                    is_enabled = EXCLUDED.is_enabled,
                    metadata = EXCLUDED.metadata,
                    updated_at = NOW(),
                    version = quota_policies.version + 1
                RETURNING *
                """;

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", IdGenerator.newId());
        params.put("tenantId", requireTenant());
        params.put("workspaceId", nullable(request.getWorkspaceId()));
        params.put("metricKey", request.getMetricKey());
        params.put("softLimit", request.getSoftLimit());
        params.put("hardLimit", request.getHardLimit());
        params.put("overageRate", request.getOverageRate());
        params.put("isEnabled", request.getEnabled() == null || request.getEnabled());
        params.put("metadata", toJson(request.getMetadata()));
        params.put("createdBy", currentActor());
        Map<String, Object> saved = repository.queryForMap(sql, params);
        recordAudit("QUOTA_POLICY_UPSERT", "QuotaPolicy", saved.get("id"), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listQuotaPolicies() {
        return repository.listByTenant("quota_policies", requireTenant(), "metric_key ASC");
    }

    @Transactional
    public Map<String, Object> incrementUsage(CorePlatformDto.UsageIncrementRequest request) {
        LocalDate periodStart = request.getPeriodStart() != null ? request.getPeriodStart() : LocalDate.now().withDayOfMonth(1);
        LocalDate periodEnd = request.getPeriodEnd() != null ? request.getPeriodEnd() : periodStart.plusMonths(1).minusDays(1);
        long delta = request.getDelta() == null ? 1L : request.getDelta();

        String sql = """
                INSERT INTO usage_counters (id, tenant_id, workspace_id, metric_key, period_start, period_end, value, metadata, created_at, updated_at, created_by, version)
                VALUES (:id, :tenantId, :workspaceId, :metricKey, :periodStart, :periodEnd, :delta, '{}'::jsonb, NOW(), NOW(), :createdBy, 0)
                ON CONFLICT (tenant_id, COALESCE(workspace_id, ''), metric_key, period_start, period_end)
                WHERE deleted_at IS NULL
                DO UPDATE SET
                    value = usage_counters.value + :delta,
                    updated_at = NOW(),
                    version = usage_counters.version + 1
                RETURNING *
                """;

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", IdGenerator.newId());
        params.put("tenantId", requireTenant());
        params.put("workspaceId", nullable(request.getWorkspaceId()));
        params.put("metricKey", request.getMetricKey());
        params.put("periodStart", periodStart);
        params.put("periodEnd", periodEnd);
        params.put("delta", delta);
        params.put("createdBy", currentActor());
        Map<String, Object> saved = repository.queryForMap(sql, params);
        recordAudit("USAGE_INCREMENT", "UsageCounter", saved.get("id"), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listUsage() {
        return repository.listByTenant("usage_counters", requireTenant(), "period_start DESC, metric_key ASC");
    }

    @Transactional
    public Map<String, Object> createSubscription(CorePlatformDto.SubscriptionRequest request) {
        String sql = """
                INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, status, billing_cycle, starts_at, ends_at, auto_renew, metadata, created_at, updated_at, created_by, version)
                SELECT :id, :tenantId, p.id, :status, :billingCycle, COALESCE(:startsAt, NOW()), :endsAt, :autoRenew, CAST(:metadata AS jsonb), NOW(), NOW(), :createdBy, 0
                FROM subscription_plans p
                WHERE p.plan_key = :planKey AND p.deleted_at IS NULL
                RETURNING *
                """;

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", IdGenerator.newId());
        params.put("tenantId", requireTenant());
        params.put("status", "ACTIVE");
        params.put("billingCycle", defaultValue(request.getBillingCycle(), "MONTHLY"));
        params.put("startsAt", request.getStartsAt());
        params.put("endsAt", request.getEndsAt());
        params.put("autoRenew", request.getAutoRenew() == null || request.getAutoRenew());
        params.put("metadata", toJson(request.getMetadata()));
        params.put("createdBy", currentActor());
        params.put("planKey", request.getPlanKey());
        Map<String, Object> saved = repository.queryForMap(sql, params);
        recordAudit("SUBSCRIPTION_CREATE", "TenantSubscription", saved.get("id"), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSubscriptions() {
        String sql = """
                SELECT ts.*, sp.plan_key, sp.display_name
                FROM tenant_subscriptions ts
                JOIN subscription_plans sp ON sp.id = ts.plan_id
                WHERE ts.tenant_id = :tenantId AND ts.deleted_at IS NULL
                ORDER BY ts.created_at DESC
                """;
        return repository.queryForList(sql, Map.of("tenantId", requireTenant()));
    }

    @Transactional
    public Map<String, Object> createEnvironment(CorePlatformDto.EnvironmentRequest request) {
        assertExists("workspaces", request.getWorkspaceId(), "workspaceId");
        Map<String, Object> values = baseValues(requireTenant());
        values.put("workspace_id", request.getWorkspaceId());
        values.put("environment_key", request.getEnvironmentKey().toUpperCase());
        values.put("display_name", request.getDisplayName());
        values.put("status", defaultValue(request.getStatus(), "ACTIVE"));
        values.put("is_locked", false);
        values.put("metadata", toJson(request.getMetadata()));
        Map<String, Object> saved = repository.insert("environments", values, List.of("metadata"));
        recordAudit("ENVIRONMENT_CREATE", "Environment", saved.get("id"), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listEnvironments() {
        return repository.listByTenant("environments", requireTenant(), "created_at DESC");
    }

    @Transactional
    public Map<String, Object> lockEnvironment(CorePlatformDto.EnvironmentLockRequest request) {
        String envSql = "SELECT workspace_id FROM environments WHERE id = :environmentId AND tenant_id = :tenantId AND deleted_at IS NULL";
        Map<String, Object> env = repository.queryForMap(envSql, Map.of("environmentId", request.getEnvironmentId(), "tenantId", requireTenant()));

        Map<String, Object> values = baseValues(requireTenant());
        values.put("workspace_id", env.get("workspace_id"));
        values.put("environment_id", request.getEnvironmentId());
        values.put("lock_type", request.getLockType());
        values.put("locked_by", currentActor());
        values.put("reason", nullable(request.getReason()));
        values.put("expires_at", request.getExpiresAt());
        values.put("is_active", true);
        values.put("metadata", "{}");

        Map<String, Object> lock = repository.insert("environment_locks", values, List.of("metadata"));
        repository.queryForMap(
                "UPDATE environments SET is_locked = TRUE, updated_at = NOW(), version = version + 1 WHERE id = :id AND tenant_id = :tenantId RETURNING *",
                Map.of("id", request.getEnvironmentId(), "tenantId", requireTenant())
        );
        recordAudit("ENVIRONMENT_LOCK", "EnvironmentLock", lock.get("id"), lock);
        return lock;
    }

    @Transactional
    public Map<String, Object> createPromotion(CorePlatformDto.PromotionRequest request) {
        Map<String, Object> values = baseValues(requireTenant());
        values.put("workspace_id", request.getWorkspaceId());
        values.put("from_environment_id", request.getFromEnvironmentId());
        values.put("to_environment_id", request.getToEnvironmentId());
        values.put("requested_by", currentActor());
        values.put("approved_by", null);
        values.put("status", "PENDING");
        values.put("summary", nullable(request.getSummary()));
        values.put("changeset", toJson(request.getChangeset()));
        values.put("approved_at", null);
        Map<String, Object> saved = repository.insert("promotion_requests", values, List.of("changeset"));
        recordAudit("PROMOTION_CREATE", "PromotionRequest", saved.get("id"), saved);
        return saved;
    }

    @Transactional
    public Map<String, Object> decidePromotion(String promotionId, CorePlatformDto.PromotionDecisionRequest request) {
        Map<String, Object> updated = repository.updateById(
                "promotion_requests",
                promotionId,
                requireTenant(),
                mapWithNullable(
                        "status", request.getStatus().toUpperCase(),
                        "approved_by", currentActor(),
                        "approved_at", "APPROVED".equalsIgnoreCase(request.getStatus()) ? Instant.now() : null,
                        "summary", nullable(request.getDecisionNote())
                ),
                Collections.emptyList()
        );
        recordAudit("PROMOTION_DECIDE", "PromotionRequest", updated.get("id"), updated);
        return updated;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPromotions() {
        return repository.listByTenant("promotion_requests", requireTenant(), "created_at DESC");
    }

    @Transactional
    public Map<String, Object> upsertFeatureControl(CorePlatformDto.FeatureControlRequest request) {
        String sql = """
                INSERT INTO feature_controls (id, tenant_id, workspace_id, feature_key, enabled, source, dependency_keys, metadata, created_at, updated_at, created_by, version)
                VALUES (:id, :tenantId, :workspaceId, :featureKey, :enabled, :source, CAST(:dependencyKeys AS jsonb), CAST(:metadata AS jsonb), NOW(), NOW(), :createdBy, 0)
                ON CONFLICT (tenant_id, COALESCE(workspace_id, ''), feature_key)
                WHERE deleted_at IS NULL
                DO UPDATE SET
                    enabled = EXCLUDED.enabled,
                    source = EXCLUDED.source,
                    dependency_keys = EXCLUDED.dependency_keys,
                    metadata = EXCLUDED.metadata,
                    updated_at = NOW(),
                    version = feature_controls.version + 1
                RETURNING *
                """;

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", IdGenerator.newId());
        params.put("tenantId", requireTenant());
        params.put("workspaceId", nullable(request.getWorkspaceId()));
        params.put("featureKey", request.getFeatureKey());
        params.put("enabled", request.getEnabled() != null && request.getEnabled());
        params.put("source", defaultValue(request.getSource(), "TENANT"));
        params.put("dependencyKeys", toJson(request.getDependencyKeys()));
        params.put("metadata", toJson(request.getMetadata()));
        params.put("createdBy", currentActor());
        Map<String, Object> saved = repository.queryForMap(sql, params);
        recordAudit("FEATURE_CONTROL_UPSERT", "FeatureControl", saved.get("id"), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFeatureControls() {
        return repository.listByTenant("feature_controls", requireTenant(), "feature_key ASC");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAuditEvents(String workspaceId, String action, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", requireTenant());
        params.put("limit", Math.max(1, Math.min(limit, 500)));

        StringBuilder sql = new StringBuilder("""
                SELECT * FROM core_audit_events
                WHERE tenant_id = :tenantId
                  AND deleted_at IS NULL
                """);

        String workspaceFilter = nullable(workspaceId);
        if (workspaceFilter != null) {
            sql.append(" AND workspace_id = :workspaceId");
            params.put("workspaceId", workspaceFilter);
        }

        String actionFilter = nullable(action);
        if (actionFilter != null) {
            sql.append(" AND action = :action");
            params.put("action", actionFilter);
        }

        sql.append(" ORDER BY created_at DESC LIMIT :limit");
        return repository.queryForList(sql.toString(), params);
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

    private Map<String, Object> baseValues(String tenantId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", IdGenerator.newId());
        values.put("tenant_id", tenantId);
        values.put("created_at", Instant.now());
        values.put("updated_at", Instant.now());
        values.put("created_by", currentActor());
        values.put("deleted_at", null);
        values.put("version", 0L);
        return values;
    }

    private String requireTenant() {
        return TenantContext.requireTenantId();
    }

    private String currentActor() {
        String actor = TenantContext.getUserId();
        return actor == null || actor.isBlank() ? "SYSTEM" : actor;
    }

    private String defaultValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String nullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private String toJson(Object value) {
        Object safe = value;
        if (safe == null) {
            safe = Collections.emptyMap();
        }
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize payload to JSON", e);
        }
    }

    private Map<String, Object> mapWithNullable(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private void assertExists(String table, String id, String fieldName) {
        List<Map<String, Object>> rows = repository.queryForList(
                "SELECT id FROM " + table + " WHERE id = :id AND tenant_id = :tenantId AND deleted_at IS NULL LIMIT 1",
                Map.of("id", id, "tenantId", requireTenant())
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is invalid for current tenant");
        }
    }
}

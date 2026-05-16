package com.legent.foundation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.util.IdGenerator;
import com.legent.foundation.dto.CorePlatformDto;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.security.RbacEvaluator;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CorePlatformService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CorePlatformRepository repository;
    private final ObjectMapper objectMapper;
    private final AdminOperationsService adminOperationsService;
    private final RbacEvaluator rbacEvaluator;

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
        String id = IdGenerator.newId();
        String parentId = nullable(request.getParentId());
        int depth = 0;
        String path = null;
        if (parentId != null) {
            Map<String, Object> parent = repository.queryForMap(
                    "SELECT id, organization_id, path, depth FROM business_units WHERE id = :id AND tenant_id = :tenantId AND deleted_at IS NULL",
                    Map.of("id", parentId, "tenantId", requireTenant())
            );
            if (!request.getOrganizationId().equals(String.valueOf(parent.get("organization_id")))) {
                throw new IllegalArgumentException("parentId must belong to same organization");
            }
            depth = toInt(parent.get("depth")) + 1;
            path = defaultValue((String) parent.get("path"), String.valueOf(parent.get("id"))) + "/" + id;
        }
        if (path == null) {
            path = id;
        }
        Map<String, Object> values = baseValues(requireTenant());
        values.put("id", id);
        values.put("organization_id", request.getOrganizationId());
        values.put("parent_id", parentId);
        values.put("code", nullable(request.getCode()));
        values.put("name", request.getName());
        values.put("description", nullable(request.getDescription()));
        values.put("status", defaultValue(request.getStatus(), "ACTIVE"));
        values.put("path", path);
        values.put("depth", depth);
        values.put("metadata", toJson(request.getMetadata()));
        Map<String, Object> saved = repository.insert("business_units", values, List.of("metadata"));
        recordAudit("BUSINESS_UNIT_CREATE", "BusinessUnit", saved.get("id"), saved);
        recordAccessSync("BUSINESS_UNIT_CHANGED", "BusinessUnit", saved, List.of("identity", "admin", "api-gateway", "navigation"));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listBusinessUnits() {
        return repository.listByTenant("business_units", requireTenant(), "created_at DESC");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listBusinessUnitTree() {
        String sql = """
                WITH RECURSIVE bu_tree AS (
                    SELECT bu.*, 0 AS tree_depth, ARRAY[bu.id::text] AS lineage
                    FROM business_units bu
                    WHERE bu.tenant_id = :tenantId
                      AND bu.deleted_at IS NULL
                      AND bu.parent_id IS NULL
                    UNION ALL
                    SELECT child.*, parent.tree_depth + 1 AS tree_depth, parent.lineage || child.id::text
                    FROM business_units child
                    JOIN bu_tree parent ON child.parent_id = parent.id
                    WHERE child.tenant_id = :tenantId
                      AND child.deleted_at IS NULL
                )
                SELECT *, array_to_string(lineage, '/') AS lineage_path
                FROM bu_tree
                ORDER BY lineage
                """;
        return repository.queryForList(sql, Map.of("tenantId", requireTenant()));
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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listWorkspaces(Set<String> roles) {
        if (canReadTenantWideCore(roles)) {
            return listWorkspaces();
        }
        return listCurrentWorkspaceOnly();
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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTeams(Set<String> roles) {
        if (canReadTenantWideCore(roles)) {
            return listTeams();
        }
        return listByCurrentWorkspace("teams", "created_at DESC");
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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDepartments(Set<String> roles) {
        if (canReadTenantWideCore(roles)) {
            return listDepartments();
        }
        return listByCurrentWorkspace("departments", "created_at DESC");
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
        recordAccessSync("MEMBERSHIP_CHANGED", "Membership", saved, List.of("identity", "admin", "campaign", "automation", "delivery", "analytics"));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMemberships() {
        return repository.listByTenant("membership_links", requireTenant(), "created_at DESC");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMemberships(Set<String> roles) {
        if (canReadTenantWideCore(roles)) {
            return listMemberships();
        }
        return listByCurrentWorkspace("membership_links", "created_at DESC");
    }

    @Transactional
    public Map<String, Object> createRoleDefinition(CorePlatformDto.RoleDefinitionRequest request) {
        String tenantId = requireTenant();
        String requestedTenantId = nullable(request.getTenantId());
        if (requestedTenantId != null && !tenantId.equals(requestedTenantId)) {
            throw new AccessDeniedException("tenantId does not match the current tenant");
        }

        Map<String, Object> values = baseValues(tenantId);
        values.put("tenant_id", tenantId);
        values.put("role_key", request.getRoleKey());
        values.put("display_name", request.getDisplayName());
        values.put("description", nullable(request.getDescription()));
        values.put("is_system", request.getSystem() != null && request.getSystem());
        values.put("permissions", toJson(request.getPermissions()));
        values.put("metadata", toJson(request.getMetadata()));
        Map<String, Object> saved = repository.insert("role_definitions", values, List.of("permissions", "metadata"));
        recordAudit("ROLE_DEFINITION_CREATE", "RoleDefinition", saved.get("id"), saved);
        recordAccessSync("ROLE_DEFINITION_CHANGED", "RoleDefinition", saved, List.of("identity", "admin", "navigation", "api-gateway", "workflow"));
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
        recordAccessSync("PERMISSION_GROUP_CHANGED", "PermissionGroup", saved, List.of("identity", "admin", "api-gateway", "workflow"));
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
    public Map<String, Object> createRoleBinding(CorePlatformDto.RoleBindingRequest request) {
        if (nullable(request.getRoleDefinitionId()) == null && nullable(request.getPermissionGroupId()) == null) {
            throw new IllegalArgumentException("roleDefinitionId or permissionGroupId is required");
        }
        if (nullable(request.getRoleDefinitionId()) != null) {
            assertScopedDefinitionExists("role_definitions", request.getRoleDefinitionId(), "roleDefinitionId");
        }
        if (nullable(request.getPermissionGroupId()) != null) {
            assertScopedDefinitionExists("permission_groups", request.getPermissionGroupId(), "permissionGroupId");
        }
        String workspaceId = nullable(request.getWorkspaceId());
        if (workspaceId != null) {
            assertExists("workspaces", workspaceId, "workspaceId");
        }
        String teamId = nullable(request.getTeamId());
        if (teamId != null) {
            assertExists("teams", teamId, "teamId");
        }
        Map<String, Object> values = baseValues(requireTenant());
        values.put("principal_type", request.getPrincipalType().trim().toUpperCase());
        values.put("principal_id", request.getPrincipalId());
        values.put("role_definition_id", nullable(request.getRoleDefinitionId()));
        values.put("permission_group_id", nullable(request.getPermissionGroupId()));
        values.put("workspace_id", workspaceId);
        values.put("team_id", teamId);
        values.put("resource_type", nullable(request.getResourceType()));
        values.put("resource_id", nullable(request.getResourceId()));
        values.put("effective_from", request.getEffectiveFrom());
        values.put("effective_until", request.getEffectiveUntil());
        values.put("metadata", toJson(request.getMetadata()));
        Map<String, Object> saved = repository.insert("principal_role_bindings", values, List.of("metadata"));
        recordAudit("PRINCIPAL_ROLE_BINDING_CREATE", "PrincipalRoleBinding", saved.get("id"), saved);
        recordAccessSync("ROLE_BINDING_CHANGED", "PrincipalRoleBinding", saved, List.of("identity", "admin", "api-gateway", "navigation"));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRoleBindings() {
        return repository.listByTenant("principal_role_bindings", requireTenant(), "created_at DESC");
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
        recordAccessSync("ACCESS_GRANT_CHANGED", "DelegatedAccessGrant", saved, List.of("identity", "admin", "api-gateway", "navigation"));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAccessGrants() {
        return repository.listByTenant("delegated_access_grants", requireTenant(), "created_at DESC");
    }

    @Transactional
    public Map<String, Object> decideAccessGrant(String id, CorePlatformDto.AccessGrantDecisionRequest request) {
        String status = request.getStatus().trim().toUpperCase();
        if (!List.of("APPROVED", "DENIED", "REVOKED").contains(status)) {
            throw new IllegalArgumentException("status must be APPROVED, DENIED, or REVOKED");
        }
        Map<String, Object> saved = repository.updateById(
                "delegated_access_grants",
                id,
                requireTenant(),
                mapWithNullable(
                        "status", status,
                        "approved_by", "APPROVED".equals(status) ? currentActor() : null,
                        "approved_at", "APPROVED".equals(status) ? Instant.now() : null,
                        "reason", nullable(request.getDecisionNote())
                ),
                Collections.emptyList()
        );
        recordAudit("ACCESS_GRANT_DECIDE", "DelegatedAccessGrant", saved.get("id"), saved);
        recordAccessSync("ACCESS_GRANT_CHANGED", "DelegatedAccessGrant", saved, List.of("identity", "admin", "api-gateway", "navigation"));
        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewAccessPolicy(String principalId) {
        if (principalId == null || principalId.isBlank()) {
            throw new IllegalArgumentException("principalId is required");
        }
        List<Map<String, Object>> bindings = repository.queryForList("""
                SELECT prb.*, rd.role_key, rd.display_name AS role_name, rd.permissions AS role_permissions,
                       pg.group_key, pg.display_name AS group_name, pg.permissions AS group_permissions
                FROM principal_role_bindings prb
                LEFT JOIN role_definitions rd ON rd.id = prb.role_definition_id AND rd.deleted_at IS NULL
                LEFT JOIN permission_groups pg ON pg.id = prb.permission_group_id AND pg.deleted_at IS NULL
                WHERE prb.tenant_id = :tenantId
                  AND prb.principal_id = :principalId
                  AND prb.deleted_at IS NULL
                  AND (prb.effective_from IS NULL OR prb.effective_from <= NOW())
                  AND (prb.effective_until IS NULL OR prb.effective_until > NOW())
                ORDER BY prb.created_at DESC
                """, Map.of("tenantId", requireTenant(), "principalId", principalId));
        List<Map<String, Object>> grants = repository.queryForList("""
                SELECT * FROM delegated_access_grants
                WHERE tenant_id = :tenantId
                  AND grantee_user_id = :principalId
                  AND status = 'APPROVED'
                  AND deleted_at IS NULL
                  AND (expires_at IS NULL OR expires_at > NOW())
                ORDER BY created_at DESC
                """, Map.of("tenantId", requireTenant(), "principalId", principalId));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("principalId", principalId);
        response.put("bindings", bindings);
        response.put("delegatedAccess", grants);
        response.put("evaluatedAt", Instant.now());
        return response;
    }

    @Transactional
    public Map<String, Object> upsertIdentityProvider(CorePlatformDto.IdentityProviderRequest request) {
        String protocol = request.getProtocol().trim().toUpperCase();
        if (!List.of("SAML", "OIDC").contains(protocol)) {
            throw new IllegalArgumentException("protocol must be SAML or OIDC");
        }
        String sql = """
                INSERT INTO enterprise_identity_providers
                    (id, tenant_id, provider_key, display_name, protocol, status, issuer, entity_id, sso_url, jwks_url,
                     metadata_url, attribute_mapping, certificate_fingerprint, signing_certificate, scim_enabled,
                     jit_provisioning_enabled, default_role_keys, metadata, created_at, updated_at, created_by, version)
                VALUES
                    (:id, :tenantId, :providerKey, :displayName, :protocol, :status, :issuer, :entityId, :ssoUrl, :jwksUrl,
                     :metadataUrl, CAST(:attributeMapping AS jsonb), :certificateFingerprint, :signingCertificate, :scimEnabled,
                     :jitProvisioningEnabled, CAST(:defaultRoleKeys AS jsonb), CAST(:metadata AS jsonb), NOW(), NOW(), :createdBy, 0)
                ON CONFLICT (tenant_id, provider_key)
                WHERE deleted_at IS NULL
                DO UPDATE SET
                    display_name = EXCLUDED.display_name,
                    protocol = EXCLUDED.protocol,
                    status = EXCLUDED.status,
                    issuer = EXCLUDED.issuer,
                    entity_id = EXCLUDED.entity_id,
                    sso_url = EXCLUDED.sso_url,
                    jwks_url = EXCLUDED.jwks_url,
                    metadata_url = EXCLUDED.metadata_url,
                    attribute_mapping = EXCLUDED.attribute_mapping,
                    certificate_fingerprint = EXCLUDED.certificate_fingerprint,
                    signing_certificate = EXCLUDED.signing_certificate,
                    scim_enabled = EXCLUDED.scim_enabled,
                    jit_provisioning_enabled = EXCLUDED.jit_provisioning_enabled,
                    default_role_keys = EXCLUDED.default_role_keys,
                    metadata = EXCLUDED.metadata,
                    updated_at = NOW(),
                    version = enterprise_identity_providers.version + 1
                RETURNING *
                """;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", IdGenerator.newId());
        params.put("tenantId", requireTenant());
        params.put("providerKey", request.getProviderKey().trim().toLowerCase());
        params.put("displayName", request.getDisplayName());
        params.put("protocol", protocol);
        params.put("status", defaultValue(request.getStatus(), "ACTIVE").toUpperCase());
        params.put("issuer", nullable(request.getIssuer()));
        params.put("entityId", nullable(request.getEntityId()));
        params.put("ssoUrl", nullable(request.getSsoUrl()));
        params.put("jwksUrl", nullable(request.getJwksUrl()));
        params.put("metadataUrl", nullable(request.getMetadataUrl()));
        params.put("attributeMapping", toJson(request.getAttributeMapping()));
        params.put("certificateFingerprint", nullable(request.getCertificateFingerprint()));
        params.put("signingCertificate", nullable(request.getSigningCertificate()));
        params.put("scimEnabled", request.getScimEnabled() != null && request.getScimEnabled());
        params.put("jitProvisioningEnabled", request.getJitProvisioningEnabled() != null && request.getJitProvisioningEnabled());
        params.put("defaultRoleKeys", toJson(request.getDefaultRoleKeys()));
        params.put("metadata", toJson(request.getMetadata()));
        params.put("createdBy", currentActor());
        Map<String, Object> saved = repository.queryForMap(sql, params);
        Map<String, Object> redacted = redactIdentityProvider(saved);
        recordAudit("IDENTITY_PROVIDER_UPSERT", "IdentityProvider", saved.get("id"), redacted);
        recordAccessSync("IDENTITY_PROVIDER_CHANGED", "IdentityProvider", saved, List.of("identity", "admin", "api-gateway"));
        return redacted;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listIdentityProviders() {
        return repository.queryForList("""
                SELECT id, tenant_id, provider_key, display_name, protocol, status, issuer, entity_id, sso_url,
                       jwks_url, metadata_url, attribute_mapping, certificate_fingerprint, scim_enabled,
                       jit_provisioning_enabled, default_role_keys, metadata, created_at, updated_at, created_by, version
                FROM enterprise_identity_providers
                WHERE tenant_id = :tenantId AND deleted_at IS NULL
                ORDER BY created_at DESC
                """, Map.of("tenantId", requireTenant()));
    }

    @Transactional
    public Map<String, Object> createScimToken(CorePlatformDto.ScimTokenRequest request) {
        assertExists("enterprise_identity_providers", request.getIdentityProviderId(), "identityProviderId");
        String rawToken = newScimToken();
        Map<String, Object> values = baseValues(requireTenant());
        values.put("identity_provider_id", request.getIdentityProviderId());
        values.put("label", request.getLabel());
        values.put("token_hash", sha256(rawToken));
        values.put("scopes", toJson(request.getScopes() == null || request.getScopes().isEmpty() ? List.of("scim:read", "scim:write") : request.getScopes()));
        values.put("status", "ACTIVE");
        values.put("expires_at", request.getExpiresAt());
        values.put("last_used_at", null);
        Map<String, Object> saved = repository.insert("scim_tokens", values, List.of("scopes"));
        saved.put("token", rawToken);
        saved.remove("token_hash");
        recordAudit("SCIM_TOKEN_CREATE", "ScimToken", saved.get("id"), Map.of("id", saved.get("id"), "label", saved.get("label")));
        recordAccessSync("SCIM_TOKEN_CHANGED", "ScimToken", saved, List.of("identity", "admin", "api-gateway"));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listScimTokens() {
        return repository.queryForList("""
                SELECT id, tenant_id, identity_provider_id, label, scopes, status, expires_at, last_used_at,
                       created_at, updated_at, created_by, version
                FROM scim_tokens
                WHERE tenant_id = :tenantId AND deleted_at IS NULL
                ORDER BY created_at DESC
                """, Map.of("tenantId", requireTenant()));
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
        recordAccessSync("FEATURE_CONTROL_CHANGED", "FeatureControl", saved, List.of("admin", "navigation", "campaign", "automation", "delivery", "analytics"));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFeatureControls() {
        return repository.listByTenant("feature_controls", requireTenant(), "feature_key ASC");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAuditEvents(String workspaceId, String action, int limit) {
        return queryAuditEvents(nullable(workspaceId), action, limit);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAuditEvents(String workspaceId, String action, int limit, Set<String> roles) {
        String scopedWorkspaceId = canReadTenantWideCore(roles)
                ? nullable(workspaceId)
                : requireMatchingCurrentWorkspace(workspaceId);
        return queryAuditEvents(scopedWorkspaceId, action, limit);
    }

    private List<Map<String, Object>> queryAuditEvents(String workspaceId, String action, int limit) {
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

    private List<Map<String, Object>> listCurrentWorkspaceOnly() {
        String workspaceId = requireCurrentWorkspaceForScopedCoreRead();
        return repository.queryForList("""
                SELECT * FROM workspaces
                WHERE tenant_id = :tenantId
                  AND id = :workspaceId
                  AND deleted_at IS NULL
                ORDER BY created_at DESC
                """, Map.of("tenantId", requireTenant(), "workspaceId", workspaceId));
    }

    private List<Map<String, Object>> listByCurrentWorkspace(String table, String orderBy) {
        String workspaceId = requireCurrentWorkspaceForScopedCoreRead();
        String sql = "SELECT * FROM " + CorePlatformRepository.safeTable(table)
                + " WHERE tenant_id = :tenantId AND workspace_id = :workspaceId AND deleted_at IS NULL ORDER BY "
                + CorePlatformRepository.safeOrderBy(orderBy);
        return repository.queryForList(sql, Map.of("tenantId", requireTenant(), "workspaceId", workspaceId));
    }

    private String requireMatchingCurrentWorkspace(String requestedWorkspaceId) {
        String workspaceId = requireCurrentWorkspaceForScopedCoreRead();
        String requested = nullable(requestedWorkspaceId);
        if (requested != null && !workspaceId.equals(requested)) {
            throw new AccessDeniedException("workspaceId does not match the current workspace");
        }
        return workspaceId;
    }

    private String requireCurrentWorkspaceForScopedCoreRead() {
        String workspaceId = nullable(TenantContext.getWorkspaceId());
        if (workspaceId == null) {
            throw new AccessDeniedException("Workspace context is required for workspace-scoped core reads");
        }
        return workspaceId;
    }

    private boolean canReadTenantWideCore(Set<String> roles) {
        return rbacEvaluator.hasPermission("tenant:*", roles);
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

    private void recordAccessSync(String eventType, String resourceType, Map<String, Object> saved, List<String> targetModules) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resourceType", resourceType);
        payload.put("resourceId", saved.get("id"));
        payload.put("workspaceId", saved.get("workspace_id"));
        payload.put("status", saved.get("status"));
        payload.put("roleKey", saved.get("role_key"));
        payload.put("groupKey", saved.get("group_key"));
        payload.put("featureKey", saved.get("feature_key"));
        payload.put("permissions", saved.get("permissions"));
        payload.put("version", saved.get("version"));
        adminOperationsService.recordSyncEvent(eventType, "core-platform", targetModules, payload);
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

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private String newScimToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "scim_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash SCIM token", e);
        }
    }

    private Map<String, Object> redactIdentityProvider(Map<String, Object> identityProvider) {
        Map<String, Object> redacted = new LinkedHashMap<>(identityProvider);
        redacted.remove("signing_certificate");
        return redacted;
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

    private void assertScopedDefinitionExists(String table, String id, String fieldName) {
        List<Map<String, Object>> rows = repository.queryForList(
                "SELECT id FROM " + table + " WHERE id = :id AND (tenant_id IS NULL OR tenant_id = :tenantId) AND deleted_at IS NULL LIMIT 1",
                Map.of("id", id, "tenantId", requireTenant())
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is invalid for current tenant");
        }
    }
}

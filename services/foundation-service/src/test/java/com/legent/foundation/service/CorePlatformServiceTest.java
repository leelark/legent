package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.CorePlatformDto;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.security.RbacEvaluator;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorePlatformServiceTest {

    @Mock
    private CorePlatformRepository repository;

    @Mock
    private AdminOperationsService adminOperationsService;

    @Captor
    private ArgumentCaptor<Map<String, Object>> mapCaptor;

    private CorePlatformService service;

    @BeforeEach
    void setUp() {
        service = new CorePlatformService(repository, new ObjectMapper(), adminOperationsService, new RbacEvaluator());
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listWorkspaces_filtersToCurrentWorkspaceForWorkspaceRole() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(Map.of("id", "workspace-1", "name", "Default")));

        List<Map<String, Object>> result = service.listWorkspaces(Set.of("WORKSPACE_OWNER"));

        assertThat(result).extracting(row -> row.get("id")).containsExactly("workspace-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> params = mapCaptor;
        verify(repository).queryForList(sql.capture(), params.capture());
        verify(repository, never()).listByTenant(eq("workspaces"), anyString(), anyString());
        assertThat(sql.getValue()).contains("FROM workspaces").contains("id = :workspaceId");
        assertThat(params.getValue()).containsEntry("tenantId", "tenant-1")
                .containsEntry("workspaceId", "workspace-1");
    }

    @Test
    void listMemberships_filtersToCurrentWorkspaceForWorkspaceRole() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(Map.of("id", "membership-1", "workspace_id", "workspace-1")));

        List<Map<String, Object>> result = service.listMemberships(Set.of("WORKSPACE_OWNER"));

        assertThat(result).extracting(row -> row.get("id")).containsExactly("membership-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> params = mapCaptor;
        verify(repository).queryForList(sql.capture(), params.capture());
        verify(repository, never()).listByTenant(eq("membership_links"), anyString(), anyString());
        assertThat(sql.getValue()).contains("FROM membership_links").contains("workspace_id = :workspaceId");
        assertThat(params.getValue()).containsEntry("tenantId", "tenant-1")
                .containsEntry("workspaceId", "workspace-1");
    }

    @Test
    void listTeams_filtersToCurrentWorkspaceForWorkspaceRole() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(Map.of("id", "team-1", "workspace_id", "workspace-1")));

        List<Map<String, Object>> result = service.listTeams(Set.of("WORKSPACE_OWNER"));

        assertThat(result).extracting(row -> row.get("id")).containsExactly("team-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> params = mapCaptor;
        verify(repository).queryForList(sql.capture(), params.capture());
        verify(repository, never()).listByTenant(eq("teams"), anyString(), anyString());
        assertThat(sql.getValue()).contains("FROM teams").contains("workspace_id = :workspaceId");
        assertThat(params.getValue()).containsEntry("tenantId", "tenant-1")
                .containsEntry("workspaceId", "workspace-1");
    }

    @Test
    void listDepartments_filtersToCurrentWorkspaceForWorkspaceRole() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(Map.of("id", "department-1", "workspace_id", "workspace-1")));

        List<Map<String, Object>> result = service.listDepartments(Set.of("WORKSPACE_OWNER"));

        assertThat(result).extracting(row -> row.get("id")).containsExactly("department-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> params = mapCaptor;
        verify(repository).queryForList(sql.capture(), params.capture());
        verify(repository, never()).listByTenant(eq("departments"), anyString(), anyString());
        assertThat(sql.getValue()).contains("FROM departments").contains("workspace_id = :workspaceId");
        assertThat(params.getValue()).containsEntry("tenantId", "tenant-1")
                .containsEntry("workspaceId", "workspace-1");
    }

    @Test
    void listAuditEvents_filtersToCurrentWorkspaceForWorkspaceRole() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(Map.of("id", "audit-1", "workspace_id", "workspace-1")));

        List<Map<String, Object>> result = service.listAuditEvents(null, null, 100, Set.of("WORKSPACE_OWNER"));

        assertThat(result).extracting(row -> row.get("id")).containsExactly("audit-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> params = mapCaptor;
        verify(repository).queryForList(sql.capture(), params.capture());
        assertThat(sql.getValue()).contains("FROM core_audit_events").contains("workspace_id = :workspaceId");
        assertThat(params.getValue()).containsEntry("tenantId", "tenant-1")
                .containsEntry("workspaceId", "workspace-1");
    }

    @Test
    void listAuditEvents_rejectsOtherWorkspaceFilterForWorkspaceRole() {
        assertThatThrownBy(() -> service.listAuditEvents("workspace-2", null, 100, Set.of("WORKSPACE_OWNER")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("workspaceId does not match");

        verify(repository, never()).queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any());
    }

    @Test
    void listRoleBindings_filtersToCurrentWorkspaceForWorkspaceRole() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(Map.of("id", "binding-1", "workspace_id", "workspace-1")));

        List<Map<String, Object>> result = service.listRoleBindings(Set.of("WORKSPACE_OWNER"));

        assertThat(result).extracting(row -> row.get("id")).containsExactly("binding-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> params = mapCaptor;
        verify(repository).queryForList(sql.capture(), params.capture());
        verify(repository, never()).listByTenant(eq("principal_role_bindings"), anyString(), anyString());
        assertThat(sql.getValue()).contains("FROM principal_role_bindings").contains("workspace_id = :workspaceId");
        assertThat(params.getValue()).containsEntry("tenantId", "tenant-1")
                .containsEntry("workspaceId", "workspace-1");
    }

    @Test
    void listAccessGrants_filtersToCurrentWorkspaceForWorkspaceRole() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(Map.of("id", "grant-1", "workspace_id", "workspace-1")));

        List<Map<String, Object>> result = service.listAccessGrants(Set.of("WORKSPACE_OWNER"));

        assertThat(result).extracting(row -> row.get("id")).containsExactly("grant-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> params = mapCaptor;
        verify(repository).queryForList(sql.capture(), params.capture());
        verify(repository, never()).listByTenant(eq("delegated_access_grants"), anyString(), anyString());
        assertThat(sql.getValue()).contains("FROM delegated_access_grants").contains("workspace_id = :workspaceId");
        assertThat(params.getValue()).containsEntry("tenantId", "tenant-1")
                .containsEntry("workspaceId", "workspace-1");
    }

    @Test
    void listRoleBindings_allowsTenantAdminTenantWideRead() {
        when(repository.listByTenant(eq("principal_role_bindings"), eq("tenant-1"), eq("created_at DESC")))
                .thenReturn(List.of(Map.of("id", "binding-tenant")));
        TenantContext.setWorkspaceId(null);

        List<Map<String, Object>> result = service.listRoleBindings(Set.of("ORG_ADMIN"));

        assertThat(result).extracting(row -> row.get("id")).containsExactly("binding-tenant");
        verify(repository).listByTenant(eq("principal_role_bindings"), eq("tenant-1"), eq("created_at DESC"));
        verify(repository, never()).queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any());
    }

    @Test
    void listAccessGrants_allowsTenantAdminTenantWideRead() {
        when(repository.listByTenant(eq("delegated_access_grants"), eq("tenant-1"), eq("created_at DESC")))
                .thenReturn(List.of(Map.of("id", "grant-tenant")));
        TenantContext.setWorkspaceId(null);

        List<Map<String, Object>> result = service.listAccessGrants(Set.of("ORG_ADMIN"));

        assertThat(result).extracting(row -> row.get("id")).containsExactly("grant-tenant");
        verify(repository).listByTenant(eq("delegated_access_grants"), eq("tenant-1"), eq("created_at DESC"));
        verify(repository, never()).queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any());
    }

    @Test
    void listRoleBindings_rejectsWorkspaceRoleWithoutWorkspaceContext() {
        TenantContext.setWorkspaceId(null);

        assertThatThrownBy(() -> service.listRoleBindings(Set.of("WORKSPACE_OWNER")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Workspace context is required");

        verify(repository, never()).queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any());
        verify(repository, never()).listByTenant(anyString(), anyString(), anyString());
    }

    @Test
    void listAccessGrants_rejectsWorkspaceRoleWithoutWorkspaceContext() {
        TenantContext.setWorkspaceId(null);

        assertThatThrownBy(() -> service.listAccessGrants(Set.of("WORKSPACE_OWNER")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Workspace context is required");

        verify(repository, never()).queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any());
        verify(repository, never()).listByTenant(anyString(), anyString(), anyString());
    }

    @Test
    void previewAccessPolicy_filtersToCurrentWorkspaceForWorkspaceRole() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(Map.of("id", "binding-1")), List.of(Map.of("id", "grant-1")));

        Map<String, Object> result = service.previewAccessPolicy("user-2", Set.of("WORKSPACE_OWNER"));

        assertThat(result).containsEntry("principalId", "user-2");
        List<Map<String, Object>> bindings = (List<Map<String, Object>>) result.get("bindings");
        List<Map<String, Object>> grants = (List<Map<String, Object>>) result.get("delegatedAccess");
        assertThat(bindings).extracting(row -> row.get("id")).containsExactly("binding-1");
        assertThat(grants).extracting(row -> row.get("id")).containsExactly("grant-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> params = mapCaptor;
        verify(repository, times(2)).queryForList(sql.capture(), params.capture());
        assertThat(sql.getAllValues().get(0)).contains("FROM principal_role_bindings").contains("prb.workspace_id = :workspaceId");
        assertThat(sql.getAllValues().get(1)).contains("FROM delegated_access_grants").contains("workspace_id = :workspaceId");
        assertThat(params.getAllValues()).allSatisfy(arguments -> assertThat(arguments)
                .containsEntry("tenantId", "tenant-1")
                .containsEntry("principalId", "user-2")
                .containsEntry("workspaceId", "workspace-1"));
    }

    @Test
    void previewAccessPolicy_allowsTenantAdminTenantWideRead() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(Map.of("id", "binding-tenant")), List.of(Map.of("id", "grant-tenant")));
        TenantContext.setWorkspaceId(null);

        Map<String, Object> result = service.previewAccessPolicy("user-2", Set.of("ORG_ADMIN"));

        assertThat(result).containsEntry("principalId", "user-2");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> params = mapCaptor;
        verify(repository, times(2)).queryForList(sql.capture(), params.capture());
        assertThat(sql.getAllValues()).allSatisfy(statement -> assertThat(statement).doesNotContain("workspace_id = :workspaceId"));
        assertThat(params.getAllValues()).allSatisfy(arguments -> assertThat(arguments)
                .containsEntry("tenantId", "tenant-1")
                .containsEntry("principalId", "user-2")
                .doesNotContainKey("workspaceId"));
    }

    @Test
    void previewAccessPolicy_rejectsWorkspaceRoleWithoutWorkspaceContext() {
        TenantContext.setWorkspaceId(null);

        assertThatThrownBy(() -> service.previewAccessPolicy("user-2", Set.of("WORKSPACE_OWNER")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Workspace context is required");

        verify(repository, never()).queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any());
    }

    @Test
    void listAuditEvents_allowsTenantAdminTenantWideRead() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(Map.of("id", "audit-tenant")));
        TenantContext.setWorkspaceId(null);

        List<Map<String, Object>> result = service.listAuditEvents(null, null, 100, Set.of("ORG_ADMIN"));

        assertThat(result).extracting(row -> row.get("id")).containsExactly("audit-tenant");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> params = mapCaptor;
        verify(repository).queryForList(sql.capture(), params.capture());
        assertThat(sql.getValue()).doesNotContain("workspace_id = :workspaceId");
        assertThat(params.getValue()).containsEntry("tenantId", "tenant-1")
                .doesNotContainKey("workspaceId");
    }

    @Test
    void listWorkspaces_rejectsWorkspaceRoleWithoutWorkspaceContext() {
        TenantContext.setWorkspaceId(null);

        assertThatThrownBy(() -> service.listWorkspaces(Set.of("WORKSPACE_OWNER")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Workspace context is required");

        verify(repository, never()).queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any());
        verify(repository, never()).listByTenant(anyString(), anyString(), anyString());
    }

    @Test
    void createRoleDefinition_rejectsMismatchedRequestTenant() {
        CorePlatformDto.RoleDefinitionRequest request = roleDefinitionRequest();
        request.setTenantId("tenant-2");

        assertThatThrownBy(() -> service.createRoleDefinition(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("tenantId does not match");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
    }

    @Test
    void createRoleDefinition_usesCurrentTenantWhenRequestTenantIsBlank() {
        CorePlatformDto.RoleDefinitionRequest request = roleDefinitionRequest();
        request.setTenantId(" ");
        when(repository.insert(eq("role_definitions"), ArgumentMatchers.<Map<String, Object>>any(), eq(List.of("permissions", "metadata"))))
                .thenReturn(Map.of("id", "role-1", "tenant_id", "tenant-1", "role_key", "workspace.viewer"));

        Map<String, Object> result = service.createRoleDefinition(request);

        assertThat(result).containsEntry("tenant_id", "tenant-1");

        ArgumentCaptor<Map<String, Object>> values = mapCaptor;
        verify(repository).insert(eq("role_definitions"), values.capture(), eq(List.of("permissions", "metadata")));
        assertThat(values.getValue())
                .containsEntry("tenant_id", "tenant-1")
                .containsEntry("permissions", "[\"tenant:read\"]")
                .containsEntry("metadata", "{}");
    }

    @Test
    void createRoleDefinition_defaultsNullPermissionsToEmptyJson() {
        CorePlatformDto.RoleDefinitionRequest request = roleDefinitionRequest();
        request.setPermissions(null);
        when(repository.insert(eq("role_definitions"), ArgumentMatchers.<Map<String, Object>>any(), eq(List.of("permissions", "metadata"))))
                .thenReturn(Map.of("id", "role-1", "tenant_id", "tenant-1", "role_key", "workspace.viewer"));

        service.createRoleDefinition(request);

        verify(repository).insert(eq("role_definitions"), mapCaptor.capture(), eq(List.of("permissions", "metadata")));
        assertThat(mapCaptor.getValue())
                .containsEntry("permissions", "[]")
                .containsEntry("metadata", "{}");
    }

    @Test
    void createPermissionGroup_rejectsMismatchedRequestTenant() {
        CorePlatformDto.PermissionGroupRequest request = permissionGroupRequest();
        request.setTenantId("tenant-2");

        assertThatThrownBy(() -> service.createPermissionGroup(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("tenantId does not match");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
    }

    @Test
    void createPermissionGroup_serializesPermissionsAsJsonArray() {
        CorePlatformDto.PermissionGroupRequest request = permissionGroupRequest();
        when(repository.insert(eq("permission_groups"), ArgumentMatchers.<Map<String, Object>>any(), eq(List.of("permissions", "metadata"))))
                .thenReturn(Map.of("id", "group-1", "tenant_id", "tenant-1", "group_key", "workspace.ops"));

        service.createPermissionGroup(request);

        verify(repository).insert(eq("permission_groups"), mapCaptor.capture(), eq(List.of("permissions", "metadata")));
        assertThat(mapCaptor.getValue())
                .containsEntry("permissions", "[\"workspace:read\"]")
                .containsEntry("metadata", "{}");
    }

    @Test
    void createPermissionGroup_defaultsNullPermissionsToEmptyJson() {
        CorePlatformDto.PermissionGroupRequest request = permissionGroupRequest();
        request.setPermissions(null);
        when(repository.insert(eq("permission_groups"), ArgumentMatchers.<Map<String, Object>>any(), eq(List.of("permissions", "metadata"))))
                .thenReturn(Map.of("id", "group-1", "tenant_id", "tenant-1", "group_key", "workspace.ops"));

        service.createPermissionGroup(request);

        verify(repository).insert(eq("permission_groups"), mapCaptor.capture(), eq(List.of("permissions", "metadata")));
        assertThat(mapCaptor.getValue())
                .containsEntry("permissions", "[]")
                .containsEntry("metadata", "{}");
    }

    @Test
    void createTeam_allowsCurrentWorkspace() {
        stubTenantRow("workspaces", "workspace-1", Map.of(
                "id", "workspace-1",
                "organization_id", "org-1"
        ));
        when(repository.insert(eq("teams"), ArgumentMatchers.<Map<String, Object>>any(), eq(List.of("metadata"))))
                .thenReturn(Map.of("id", "team-1", "workspace_id", "workspace-1"));

        Map<String, Object> result = service.createTeam(teamRequest("workspace-1"));

        assertThat(result).containsEntry("workspace_id", "workspace-1");
        verify(repository).insert(eq("teams"), mapCaptor.capture(), eq(List.of("metadata")));
        assertThat(mapCaptor.getValue()).containsEntry("workspace_id", "workspace-1");
    }

    @Test
    void createTeam_rejectsWorkspaceOutsideCurrentContextBeforeInsert() {
        assertThatThrownBy(() -> service.createTeam(teamRequest("workspace-2")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("workspaceId does not match");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
    }

    @Test
    void createDepartment_allowsCurrentWorkspace() {
        stubTenantRow("workspaces", "workspace-1", Map.of(
                "id", "workspace-1",
                "organization_id", "org-1"
        ));
        when(repository.insert(eq("departments"), ArgumentMatchers.<Map<String, Object>>any(), eq(List.of("metadata"))))
                .thenReturn(Map.of("id", "department-1", "workspace_id", "workspace-1"));

        Map<String, Object> result = service.createDepartment(departmentRequest("workspace-1"));

        assertThat(result).containsEntry("workspace_id", "workspace-1");
        verify(repository).insert(eq("departments"), mapCaptor.capture(), eq(List.of("metadata")));
        assertThat(mapCaptor.getValue()).containsEntry("workspace_id", "workspace-1");
    }

    @Test
    void createDepartment_rejectsWorkspaceOutsideCurrentContextBeforeInsert() {
        assertThatThrownBy(() -> service.createDepartment(departmentRequest("workspace-2")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("workspaceId does not match");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
    }

    @Test
    void createMembership_allowsSameWorkspaceTeamAndDepartment() {
        stubTenantRow("organizations", "org-1", Map.of("id", "org-1"));
        stubTenantRow("workspaces", "workspace-1", Map.of(
                "id", "workspace-1",
                "organization_id", "org-1"
        ));
        stubTenantRow("teams", "team-1", Map.of("id", "team-1", "workspace_id", "workspace-1"));
        stubTenantRow("departments", "department-1", Map.of("id", "department-1", "workspace_id", "workspace-1"));
        when(repository.insert(eq("membership_links"), ArgumentMatchers.<Map<String, Object>>any(), eq(List.of("metadata"))))
                .thenReturn(Map.of("id", "membership-1", "workspace_id", "workspace-1", "team_id", "team-1", "department_id", "department-1"));

        Map<String, Object> result = service.createMembership(membershipRequest("workspace-1", "team-1", "department-1"));

        assertThat(result).containsEntry("workspace_id", "workspace-1");
        verify(repository).insert(eq("membership_links"), mapCaptor.capture(), eq(List.of("metadata")));
        assertThat(mapCaptor.getValue())
                .containsEntry("workspace_id", "workspace-1")
                .containsEntry("team_id", "team-1")
                .containsEntry("department_id", "department-1");
    }

    @Test
    void createMembership_rejectsTeamOutsideMembershipWorkspaceBeforeInsert() {
        stubTenantRow("organizations", "org-1", Map.of("id", "org-1"));
        stubTenantRow("workspaces", "workspace-1", Map.of(
                "id", "workspace-1",
                "organization_id", "org-1"
        ));
        stubTenantRow("teams", "team-2", Map.of("id", "team-2", "workspace_id", "workspace-2"));

        assertThatThrownBy(() -> service.createMembership(membershipRequest("workspace-1", "team-2", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamId must belong to workspaceId");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
    }

    @Test
    void createMembership_rejectsDepartmentOutsideMembershipWorkspaceBeforeInsert() {
        stubTenantRow("organizations", "org-1", Map.of("id", "org-1"));
        stubTenantRow("workspaces", "workspace-1", Map.of(
                "id", "workspace-1",
                "organization_id", "org-1"
        ));
        stubTenantRow("departments", "department-2", Map.of("id", "department-2", "workspace_id", "workspace-2"));

        assertThatThrownBy(() -> service.createMembership(membershipRequest("workspace-1", null, "department-2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("departmentId must belong to workspaceId");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
    }

    @Test
    void createMembership_rejectsWorkspaceOutsideCurrentContextBeforeInsert() {
        stubTenantRow("organizations", "org-1", Map.of("id", "org-1"));

        assertThatThrownBy(() -> service.createMembership(membershipRequest("workspace-2", null, null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("workspaceId does not match");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
    }

    @Test
    void createMembership_rejectsTeamWithoutWorkspaceBeforeInsert() {
        stubTenantRow("organizations", "org-1", Map.of("id", "org-1"));

        assertThatThrownBy(() -> service.createMembership(membershipRequest(null, "team-1", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId is required when teamId is provided");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
    }

    @Test
    void createRoleBinding_allowsSameWorkspaceUserPrincipal() {
        stubTenantRow("role_definitions", "role-1", Map.of("id", "role-1"));
        stubTenantRow("workspaces", "workspace-1", Map.of(
                "id", "workspace-1",
                "organization_id", "org-1"
        ));
        stubActiveMembership("user-2", "workspace-1", true);
        when(repository.insert(eq("principal_role_bindings"), ArgumentMatchers.<Map<String, Object>>any(), eq(List.of("metadata"))))
                .thenReturn(Map.of("id", "binding-1", "workspace_id", "workspace-1", "principal_id", "user-2"));

        Map<String, Object> result = service.createRoleBinding(roleBindingRequest("USER", "user-2", "workspace-1"));

        assertThat(result).containsEntry("workspace_id", "workspace-1");
        verify(repository).insert(eq("principal_role_bindings"), mapCaptor.capture(), eq(List.of("metadata")));
        assertThat(mapCaptor.getValue())
                .containsEntry("principal_type", "USER")
                .containsEntry("principal_id", "user-2")
                .containsEntry("workspace_id", "workspace-1");
    }

    @Test
    void createRoleBinding_rejectsTeamOutsideBindingWorkspaceBeforeInsert() {
        CorePlatformDto.RoleBindingRequest request = roleBindingRequest("USER", "user-2", "workspace-1");
        request.setTeamId("team-2");
        stubTenantRow("role_definitions", "role-1", Map.of("id", "role-1"));
        stubTenantRow("workspaces", "workspace-1", Map.of(
                "id", "workspace-1",
                "organization_id", "org-1"
        ));
        stubTenantRow("teams", "team-2", Map.of("id", "team-2", "workspace_id", "workspace-2"));

        assertThatThrownBy(() -> service.createRoleBinding(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamId must belong to workspaceId");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
    }

    @Test
    void createRoleBinding_rejectsPrincipalWithoutMembershipInBindingWorkspaceBeforeInsert() {
        stubTenantRow("role_definitions", "role-1", Map.of("id", "role-1"));
        stubTenantRow("workspaces", "workspace-1", Map.of(
                "id", "workspace-1",
                "organization_id", "org-1"
        ));
        stubActiveMembership("user-2", "workspace-1", false);

        assertThatThrownBy(() -> service.createRoleBinding(roleBindingRequest("USER", "user-2", "workspace-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("principalId must have an active membership");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
    }

    @Test
    void createRoleBinding_rejectsResourceOutsideBindingWorkspaceBeforeInsert() {
        CorePlatformDto.RoleBindingRequest request = roleBindingRequest("USER", "user-2", "workspace-1");
        request.setResourceType("TEAM");
        request.setResourceId("team-2");
        stubTenantRow("role_definitions", "role-1", Map.of("id", "role-1"));
        stubTenantRow("workspaces", "workspace-1", Map.of(
                "id", "workspace-1",
                "organization_id", "org-1"
        ));
        stubActiveMembership("user-2", "workspace-1", true);
        stubTenantRow("teams", "team-2", Map.of("id", "team-2", "workspace_id", "workspace-2"));

        assertThatThrownBy(() -> service.createRoleBinding(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resourceId must belong to workspaceId");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
    }

    @Test
    void createRoleBinding_rejectsWorkspaceOutsideCurrentContextBeforeInsert() {
        stubTenantRow("role_definitions", "role-1", Map.of("id", "role-1"));

        assertThatThrownBy(() -> service.createRoleBinding(roleBindingRequest("USER", "user-2", "workspace-2")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("workspaceId does not match");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
    }

    @Test
    void createAccessGrant_allowsSameWorkspaceGrantee() {
        stubTenantRow("workspaces", "workspace-1", Map.of(
                "id", "workspace-1",
                "organization_id", "org-1"
        ));
        stubActiveMembership("user-2", "workspace-1", true);
        when(repository.insert(eq("delegated_access_grants"), ArgumentMatchers.<Map<String, Object>>any(), eq(List.of("permissions"))))
                .thenReturn(Map.of("id", "grant-1", "workspace_id", "workspace-1", "grantee_user_id", "user-2"));

        Map<String, Object> result = service.createAccessGrant(accessGrantRequest("user-2", "workspace-1"));

        assertThat(result).containsEntry("workspace_id", "workspace-1");
        verify(repository).insert(eq("delegated_access_grants"), mapCaptor.capture(), eq(List.of("permissions")));
        assertThat(mapCaptor.getValue())
                .containsEntry("grantee_user_id", "user-2")
                .containsEntry("workspace_id", "workspace-1")
                .containsEntry("permissions", "[\"workspace:read\"]");
    }

    @Test
    void createAccessGrant_defaultsNullPermissionsToEmptyJson() {
        CorePlatformDto.AccessGrantRequest request = accessGrantRequest("user-2", "workspace-1");
        request.setPermissions(null);
        stubTenantRow("workspaces", "workspace-1", Map.of(
                "id", "workspace-1",
                "organization_id", "org-1"
        ));
        stubActiveMembership("user-2", "workspace-1", true);
        when(repository.insert(eq("delegated_access_grants"), ArgumentMatchers.<Map<String, Object>>any(), eq(List.of("permissions"))))
                .thenReturn(Map.of("id", "grant-1", "workspace_id", "workspace-1", "grantee_user_id", "user-2"));

        service.createAccessGrant(request);

        verify(repository).insert(eq("delegated_access_grants"), mapCaptor.capture(), eq(List.of("permissions")));
        assertThat(mapCaptor.getValue())
                .containsEntry("workspace_id", "workspace-1")
                .containsEntry("permissions", "[]");
    }

    @Test
    void createAccessGrant_rejectsGranteeOutsideGrantWorkspaceBeforeInsert() {
        stubTenantRow("workspaces", "workspace-1", Map.of(
                "id", "workspace-1",
                "organization_id", "org-1"
        ));
        stubActiveMembership("user-2", "workspace-1", false);

        assertThatThrownBy(() -> service.createAccessGrant(accessGrantRequest("user-2", "workspace-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("granteeUserId must have an active membership");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
    }

    @Test
    void createAccessGrant_rejectsWorkspaceOutsideCurrentContextBeforeInsert() {
        assertThatThrownBy(() -> service.createAccessGrant(accessGrantRequest("user-2", "workspace-2")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("workspaceId does not match");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
    }

    private CorePlatformDto.RoleDefinitionRequest roleDefinitionRequest() {
        CorePlatformDto.RoleDefinitionRequest request = new CorePlatformDto.RoleDefinitionRequest();
        request.setRoleKey("workspace.viewer");
        request.setDisplayName("Workspace Viewer");
        request.setPermissions(List.of("tenant:read"));
        return request;
    }

    private CorePlatformDto.PermissionGroupRequest permissionGroupRequest() {
        CorePlatformDto.PermissionGroupRequest request = new CorePlatformDto.PermissionGroupRequest();
        request.setGroupKey("workspace.ops");
        request.setDisplayName("Workspace Ops");
        request.setPermissions(List.of("workspace:read"));
        return request;
    }

    private CorePlatformDto.TeamRequest teamRequest(String workspaceId) {
        CorePlatformDto.TeamRequest request = new CorePlatformDto.TeamRequest();
        request.setWorkspaceId(workspaceId);
        request.setName("Lifecycle Team");
        request.setCode("LIFE");
        request.setStatus("ACTIVE");
        return request;
    }

    private CorePlatformDto.DepartmentRequest departmentRequest(String workspaceId) {
        CorePlatformDto.DepartmentRequest request = new CorePlatformDto.DepartmentRequest();
        request.setWorkspaceId(workspaceId);
        request.setName("Lifecycle Department");
        request.setCode("LIFE");
        return request;
    }

    private CorePlatformDto.MembershipRequest membershipRequest(String workspaceId, String teamId, String departmentId) {
        CorePlatformDto.MembershipRequest request = new CorePlatformDto.MembershipRequest();
        request.setUserId("user-2");
        request.setOrganizationId("org-1");
        request.setWorkspaceId(workspaceId);
        request.setTeamId(teamId);
        request.setDepartmentId(departmentId);
        request.setPrincipalType("USER");
        return request;
    }

    private CorePlatformDto.RoleBindingRequest roleBindingRequest(String principalType, String principalId, String workspaceId) {
        CorePlatformDto.RoleBindingRequest request = new CorePlatformDto.RoleBindingRequest();
        request.setPrincipalType(principalType);
        request.setPrincipalId(principalId);
        request.setRoleDefinitionId("role-1");
        request.setWorkspaceId(workspaceId);
        return request;
    }

    private CorePlatformDto.AccessGrantRequest accessGrantRequest(String granteeUserId, String workspaceId) {
        CorePlatformDto.AccessGrantRequest request = new CorePlatformDto.AccessGrantRequest();
        request.setGranteeUserId(granteeUserId);
        request.setWorkspaceId(workspaceId);
        request.setPermissions(List.of("workspace:read"));
        return request;
    }

    private void stubTenantRow(String table, String id, Map<String, Object> row) {
        when(repository.queryForList(
                ArgumentMatchers.argThat(sql -> sql != null && sql.contains("FROM " + table) && sql.contains("id = :id")),
                ArgumentMatchers.<Map<String, Object>>argThat(params -> id.equals(params.get("id")))))
                .thenReturn(List.of(row));
    }

    private void stubActiveMembership(String userId, String workspaceId, boolean exists) {
        when(repository.queryForList(
                ArgumentMatchers.argThat(sql -> sql != null
                        && sql.contains("FROM membership_links")
                        && sql.contains("user_id = :userId")
                        && sql.contains("workspace_id = :workspaceId")),
                ArgumentMatchers.<Map<String, Object>>argThat(params ->
                        userId.equals(params.get("userId")) && workspaceId.equals(params.get("workspaceId")))))
                .thenReturn(exists ? List.of(Map.of("id", "membership-1")) : List.of());
    }
}

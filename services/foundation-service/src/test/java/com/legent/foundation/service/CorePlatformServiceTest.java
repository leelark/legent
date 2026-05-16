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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorePlatformServiceTest {

    @Mock
    private CorePlatformRepository repository;

    @Mock
    private AdminOperationsService adminOperationsService;

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
        when(repository.queryForList(anyString(), any(Map.class)))
                .thenReturn(List.of(Map.of("id", "workspace-1", "name", "Default")));

        List<Map<String, Object>> result = service.listWorkspaces(Set.of("WORKSPACE_OWNER"));

        assertThat(result).extracting(row -> row.get("id")).containsExactly("workspace-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(repository).queryForList(sql.capture(), params.capture());
        verify(repository, never()).listByTenant(eq("workspaces"), anyString(), anyString());
        assertThat(sql.getValue()).contains("FROM workspaces").contains("id = :workspaceId");
        assertThat(params.getValue()).containsEntry("tenantId", "tenant-1")
                .containsEntry("workspaceId", "workspace-1");
    }

    @Test
    void listMemberships_filtersToCurrentWorkspaceForWorkspaceRole() {
        when(repository.queryForList(anyString(), any(Map.class)))
                .thenReturn(List.of(Map.of("id", "membership-1", "workspace_id", "workspace-1")));

        List<Map<String, Object>> result = service.listMemberships(Set.of("WORKSPACE_OWNER"));

        assertThat(result).extracting(row -> row.get("id")).containsExactly("membership-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(repository).queryForList(sql.capture(), params.capture());
        verify(repository, never()).listByTenant(eq("membership_links"), anyString(), anyString());
        assertThat(sql.getValue()).contains("FROM membership_links").contains("workspace_id = :workspaceId");
        assertThat(params.getValue()).containsEntry("tenantId", "tenant-1")
                .containsEntry("workspaceId", "workspace-1");
    }

    @Test
    void listTeams_filtersToCurrentWorkspaceForWorkspaceRole() {
        when(repository.queryForList(anyString(), any(Map.class)))
                .thenReturn(List.of(Map.of("id", "team-1", "workspace_id", "workspace-1")));

        List<Map<String, Object>> result = service.listTeams(Set.of("WORKSPACE_OWNER"));

        assertThat(result).extracting(row -> row.get("id")).containsExactly("team-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(repository).queryForList(sql.capture(), params.capture());
        verify(repository, never()).listByTenant(eq("teams"), anyString(), anyString());
        assertThat(sql.getValue()).contains("FROM teams").contains("workspace_id = :workspaceId");
        assertThat(params.getValue()).containsEntry("tenantId", "tenant-1")
                .containsEntry("workspaceId", "workspace-1");
    }

    @Test
    void listDepartments_filtersToCurrentWorkspaceForWorkspaceRole() {
        when(repository.queryForList(anyString(), any(Map.class)))
                .thenReturn(List.of(Map.of("id", "department-1", "workspace_id", "workspace-1")));

        List<Map<String, Object>> result = service.listDepartments(Set.of("WORKSPACE_OWNER"));

        assertThat(result).extracting(row -> row.get("id")).containsExactly("department-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(repository).queryForList(sql.capture(), params.capture());
        verify(repository, never()).listByTenant(eq("departments"), anyString(), anyString());
        assertThat(sql.getValue()).contains("FROM departments").contains("workspace_id = :workspaceId");
        assertThat(params.getValue()).containsEntry("tenantId", "tenant-1")
                .containsEntry("workspaceId", "workspace-1");
    }

    @Test
    void listAuditEvents_filtersToCurrentWorkspaceForWorkspaceRole() {
        when(repository.queryForList(anyString(), any(Map.class)))
                .thenReturn(List.of(Map.of("id", "audit-1", "workspace_id", "workspace-1")));

        List<Map<String, Object>> result = service.listAuditEvents(null, null, 100, Set.of("WORKSPACE_OWNER"));

        assertThat(result).extracting(row -> row.get("id")).containsExactly("audit-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
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

        verify(repository, never()).queryForList(anyString(), any(Map.class));
    }

    @Test
    void listAuditEvents_allowsTenantAdminTenantWideRead() {
        when(repository.queryForList(anyString(), any(Map.class)))
                .thenReturn(List.of(Map.of("id", "audit-tenant")));
        TenantContext.setWorkspaceId(null);

        List<Map<String, Object>> result = service.listAuditEvents(null, null, 100, Set.of("ORG_ADMIN"));

        assertThat(result).extracting(row -> row.get("id")).containsExactly("audit-tenant");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
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

        verify(repository, never()).queryForList(anyString(), any(Map.class));
        verify(repository, never()).listByTenant(anyString(), anyString(), anyString());
    }

    @Test
    void createRoleDefinition_rejectsMismatchedRequestTenant() {
        CorePlatformDto.RoleDefinitionRequest request = roleDefinitionRequest();
        request.setTenantId("tenant-2");

        assertThatThrownBy(() -> service.createRoleDefinition(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("tenantId does not match");

        verify(repository, never()).insert(anyString(), any(Map.class), any(List.class));
    }

    @Test
    void createRoleDefinition_usesCurrentTenantWhenRequestTenantIsBlank() {
        CorePlatformDto.RoleDefinitionRequest request = roleDefinitionRequest();
        request.setTenantId(" ");
        when(repository.insert(eq("role_definitions"), any(Map.class), eq(List.of("permissions", "metadata"))))
                .thenReturn(Map.of("id", "role-1", "tenant_id", "tenant-1", "role_key", "workspace.viewer"));

        Map<String, Object> result = service.createRoleDefinition(request);

        assertThat(result).containsEntry("tenant_id", "tenant-1");

        ArgumentCaptor<Map<String, Object>> values = ArgumentCaptor.forClass(Map.class);
        verify(repository).insert(eq("role_definitions"), values.capture(), eq(List.of("permissions", "metadata")));
        assertThat(values.getValue()).containsEntry("tenant_id", "tenant-1");
    }

    private CorePlatformDto.RoleDefinitionRequest roleDefinitionRequest() {
        CorePlatformDto.RoleDefinitionRequest request = new CorePlatformDto.RoleDefinitionRequest();
        request.setRoleKey("workspace.viewer");
        request.setDisplayName("Workspace Viewer");
        request.setPermissions(List.of("tenant:read"));
        return request;
    }
}

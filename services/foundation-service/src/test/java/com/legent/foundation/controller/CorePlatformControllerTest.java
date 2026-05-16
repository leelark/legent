package com.legent.foundation.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.foundation.service.CorePlatformService;
import com.legent.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorePlatformControllerTest {

    @Mock
    private CorePlatformService corePlatformService;

    @Test
    void listWorkspaces_passesAuthenticatedRolesToScopedServiceRead() {
        CorePlatformController controller = new CorePlatformController(corePlatformService);
        UserPrincipal principal = principal(Set.of("WORKSPACE_OWNER"));
        when(corePlatformService.listWorkspaces(principal.getRoles()))
                .thenReturn(List.of(Map.of("id", "workspace-1")));

        ApiResponse<List<Map<String, Object>>> response = controller.listWorkspaces(principal);

        assertThat(response.getData()).extracting(row -> row.get("id")).containsExactly("workspace-1");
        verify(corePlatformService).listWorkspaces(Set.of("WORKSPACE_OWNER"));
    }

    @Test
    void listMemberships_passesAuthenticatedRolesToScopedServiceRead() {
        CorePlatformController controller = new CorePlatformController(corePlatformService);
        UserPrincipal principal = principal(Set.of("WORKSPACE_OWNER"));
        when(corePlatformService.listMemberships(principal.getRoles()))
                .thenReturn(List.of(Map.of("id", "membership-1")));

        ApiResponse<List<Map<String, Object>>> response = controller.listMemberships(principal);

        assertThat(response.getData()).extracting(row -> row.get("id")).containsExactly("membership-1");
        verify(corePlatformService).listMemberships(Set.of("WORKSPACE_OWNER"));
    }

    @Test
    void listTeams_passesAuthenticatedRolesToScopedServiceRead() {
        CorePlatformController controller = new CorePlatformController(corePlatformService);
        UserPrincipal principal = principal(Set.of("WORKSPACE_OWNER"));
        when(corePlatformService.listTeams(principal.getRoles()))
                .thenReturn(List.of(Map.of("id", "team-1")));

        ApiResponse<List<Map<String, Object>>> response = controller.listTeams(principal);

        assertThat(response.getData()).extracting(row -> row.get("id")).containsExactly("team-1");
        verify(corePlatformService).listTeams(Set.of("WORKSPACE_OWNER"));
    }

    @Test
    void listDepartments_passesAuthenticatedRolesToScopedServiceRead() {
        CorePlatformController controller = new CorePlatformController(corePlatformService);
        UserPrincipal principal = principal(Set.of("WORKSPACE_OWNER"));
        when(corePlatformService.listDepartments(principal.getRoles()))
                .thenReturn(List.of(Map.of("id", "department-1")));

        ApiResponse<List<Map<String, Object>>> response = controller.listDepartments(principal);

        assertThat(response.getData()).extracting(row -> row.get("id")).containsExactly("department-1");
        verify(corePlatformService).listDepartments(Set.of("WORKSPACE_OWNER"));
    }

    @Test
    void listAuditEvents_passesAuthenticatedRolesToScopedServiceRead() {
        CorePlatformController controller = new CorePlatformController(corePlatformService);
        UserPrincipal principal = principal(Set.of("WORKSPACE_OWNER"));
        when(corePlatformService.listAuditEvents("workspace-1", "MEMBERSHIP_CREATE", 25, principal.getRoles()))
                .thenReturn(List.of(Map.of("id", "audit-1")));

        ApiResponse<List<Map<String, Object>>> response =
                controller.listAuditEvents(principal, "workspace-1", "MEMBERSHIP_CREATE", 25);

        assertThat(response.getData()).extracting(row -> row.get("id")).containsExactly("audit-1");
        verify(corePlatformService).listAuditEvents("workspace-1", "MEMBERSHIP_CREATE", 25, Set.of("WORKSPACE_OWNER"));
    }

    private UserPrincipal principal(Set<String> roles) {
        return new UserPrincipal("user-1", "tenant-1", "workspace-1", null, roles);
    }
}

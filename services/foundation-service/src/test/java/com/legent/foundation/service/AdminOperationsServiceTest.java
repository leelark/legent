package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.repository.CorePlatformRepository;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOperationsServiceTest {

    @Mock
    private CorePlatformRepository repository;

    @Captor
    private ArgumentCaptor<String> sqlCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> paramsCaptor;

    private AdminOperationsService service;

    @BeforeEach
    void setUp() {
        service = new AdminOperationsService(repository, new ObjectMapper());
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void dashboard_requiresWorkspaceContextBeforeQuerying() {
        TenantContext.setWorkspaceId(null);

        assertThatThrownBy(() -> service.dashboard())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workspace context is required");

        verifyNoInteractions(repository);
    }

    @Test
    void accessOverview_scopesMembershipsAndDelegatedAccessToWorkspace() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of());

        service.accessOverview();

        CapturedQueries captured = captureQueryForListCalls();
        assertThat(captured.sql()).anySatisfy(sql ->
                assertThat(sql).contains("FROM membership_links").contains("workspace_id = :workspaceId"));
        assertThat(captured.sql()).anySatisfy(sql ->
                assertThat(sql).contains("FROM delegated_access_grants").contains("workspace_id IS NULL OR workspace_id = :workspaceId"));
        assertThat(captured.params()).anySatisfy(params ->
                assertThat(params).containsEntry("tenantId", "tenant-1").containsEntry("workspaceId", "workspace-1"));
    }

    @Test
    void syncEvents_scopesLedgerToWorkspace() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of());

        service.syncEvents("failed", 500);

        CapturedQueries captured = captureQueryForListCalls();
        assertThat(captured.sql()).anySatisfy(sql ->
                assertThat(sql).contains("FROM admin_sync_events").contains("workspace_id IS NULL OR workspace_id = :workspaceId"));
        assertThat(captured.params()).anySatisfy(params ->
                assertThat(params)
                        .containsEntry("tenantId", "tenant-1")
                        .containsEntry("workspaceId", "workspace-1")
                        .containsEntry("status", "FAILED")
                        .containsEntry("limit", 250));
    }

    @Test
    void dashboard_includesTenantLevelSyncAndBootstrapRows() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of());
        when(repository.queryForMap(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(Map.of("count", 0));

        service.dashboard();

        CapturedQueries listQueries = captureQueryForListCalls();
        CapturedQueries mapQueries = captureQueryForMapCalls();
        assertThat(listQueries.sql()).anySatisfy(sql ->
                assertThat(sql).contains("FROM admin_sync_events").contains("workspace_id IS NULL OR workspace_id = :workspaceId"));
        assertThat(listQueries.sql()).anySatisfy(sql ->
                assertThat(sql).contains("FROM tenant_bootstrap_status").contains("workspace_id IS NULL OR workspace_id = :workspaceId"));
        assertThat(mapQueries.sql()).anySatisfy(sql ->
                assertThat(sql).contains("FROM tenant_bootstrap_status").contains("workspace_id IS NULL OR workspace_id = :workspaceId"));
        assertThat(mapQueries.sql()).anySatisfy(sql ->
                assertThat(sql).contains("FROM admin_sync_events").contains("workspace_id IS NULL OR workspace_id = :workspaceId"));
    }

    private CapturedQueries captureQueryForListCalls() {
        verify(repository, atLeastOnce()).queryForList(sqlCaptor.capture(), paramsCaptor.capture());
        return new CapturedQueries(sqlCaptor.getAllValues(), paramsCaptor.getAllValues());
    }

    private CapturedQueries captureQueryForMapCalls() {
        verify(repository, atLeastOnce()).queryForMap(sqlCaptor.capture(), paramsCaptor.capture());
        return new CapturedQueries(sqlCaptor.getAllValues(), paramsCaptor.getAllValues());
    }

    private record CapturedQueries(List<String> sql, List<Map<String, Object>> params) {
    }
}

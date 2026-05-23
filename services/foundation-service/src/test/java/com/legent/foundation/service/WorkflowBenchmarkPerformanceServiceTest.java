package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.WorkflowBenchmarkRequest;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.foundation.service.performance.WorkflowBenchmarkService;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowBenchmarkPerformanceServiceTest {

    @Mock
    private CorePlatformRepository repository;

    private WorkflowBenchmarkService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowBenchmarkService(repository, new ObjectMapper());
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void record_computesSalesforceDeltasAndLeaderVerdict() {
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        WorkflowBenchmarkRequest request = new WorkflowBenchmarkRequest();
        request.setBenchmarkKey("campaign-create-launch");
        request.setFlowName("Campaign create and launch");
        request.setCampaignCreationSeconds(240);
        request.setLaunchErrors(1);
        request.setObservabilityScore(92);
        request.setCompetitorCreationSeconds(420);
        request.setCompetitorLaunchErrors(4);
        request.setCompetitorObservabilityScore(74);

        Map<String, Object> result = service.record(request);

        assertThat(result.get("verdict")).isEqualTo("LEADER");
        Map<?, ?> deltas = (Map<?, ?>) result.get("deltas");
        assertThat(deltas.get("campaignCreationSeconds")).isEqualTo(180);
        assertThat(deltas.get("launchErrors")).isEqualTo(3);
        assertThat(deltas.get("observabilityScore")).isEqualTo(18);
    }

    @Test
    void record_requiresWorkspaceContextEvenWhenRequestCarriesWorkspace() {
        TenantContext.setWorkspaceId(null);

        WorkflowBenchmarkRequest request = new WorkflowBenchmarkRequest();
        request.setWorkspaceId("workspace-1");

        assertThatThrownBy(() -> service.record(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId is required");
        verifyNoInteractions(repository);
    }

    @Test
    void listBenchmarks_usesExactWorkspaceScope() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(java.util.List.of());

        service.listBenchmarks(null, 10);

        verify(repository).queryForList(
                ArgumentMatchers.argThat(sql -> sql.contains("workspace_id = :workspaceId")
                        && !sql.contains(":workspaceId IS NULL")),
                ArgumentMatchers.<Map<String, Object>>argThat(params -> "workspace-1".equals(params.get("workspaceId")))
        );
    }
}

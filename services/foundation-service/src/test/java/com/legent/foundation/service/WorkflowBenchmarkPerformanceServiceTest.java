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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(repository.insert(anyString(), any(Map.class), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

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
}

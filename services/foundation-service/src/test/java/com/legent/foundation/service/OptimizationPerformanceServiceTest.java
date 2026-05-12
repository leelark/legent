package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.OptimizationEvaluateRequest;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.foundation.service.performance.ClosedLoopOptimizationService;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptimizationPerformanceServiceTest {

    @Mock
    private CorePlatformRepository repository;

    private ClosedLoopOptimizationService service;

    @BeforeEach
    void setUp() {
        service = new ClosedLoopOptimizationService(repository, new ObjectMapper());
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void evaluate_consentBlocksRevenueOptimization() {
        when(repository.queryForList(anyString(), any(Map.class))).thenReturn(List.of(revenuePolicy()));
        when(repository.insert(anyString(), any(Map.class), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        OptimizationEvaluateRequest request = new OptimizationEvaluateRequest();
        request.setPolicyKey("revenue-lift");
        request.setArtifactType("CAMPAIGN");
        request.setArtifactId("campaign-1");
        request.setSignals(Map.of(
                "consentCoveragePercent", 70,
                "revenueAtRisk", 50_000,
                "changesAudience", true
        ));

        Map<String, Object> result = service.evaluate(request);

        assertThat(result.get("approvalRequired")).isEqualTo(true);
        assertThat(result.get("rollbackRequired")).isEqualTo(true);
        assertThat((List<?>) result.get("blockedReasons")).anySatisfy(reason ->
                assertThat(String.valueOf(reason)).contains("Consent coverage"));
        assertThat(result.get("riskBand")).isEqualTo("HIGH");
    }

    @Test
    void evaluate_revenueChangeRequiresApprovalWithoutConsentBlock() {
        when(repository.queryForList(anyString(), any(Map.class))).thenReturn(List.of(revenuePolicy()));
        when(repository.insert(anyString(), any(Map.class), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        OptimizationEvaluateRequest request = new OptimizationEvaluateRequest();
        request.setPolicyKey("revenue-lift");
        request.setSignals(Map.of(
                "consentCoveragePercent", 99,
                "revenueAtRisk", 25_000,
                "conversionRateDelta", 0.04
        ));

        Map<String, Object> result = service.evaluate(request);

        assertThat(result.get("approvalRequired")).isEqualTo(true);
        assertThat((List<?>) result.get("blockedReasons")).isEmpty();
        assertThat((List<?>) result.get("recommendations")).anySatisfy(item ->
                assertThat(((Map<?, ?>) item).get("key")).isEqualTo("revenue.approval"));
    }

    private Map<String, Object> revenuePolicy() {
        return Map.of(
                "id", "policy-1",
                "workspace_id", "workspace-1",
                "policy_key", "revenue-lift",
                "optimization_type", "REVENUE",
                "guardrails", Map.of("minConsentCoveragePercent", 95),
                "approval_policy", Map.of("requireHumanApproval", false),
                "rollback_policy", Map.of("snapshotRequired", false)
        );
    }
}

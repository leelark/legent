package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.DifferentiationDto;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DifferentiationPlatformServiceTest {

    @Mock
    private CorePlatformRepository repository;

    private DifferentiationPlatformService service;

    @BeforeEach
    void setUp() {
        service = new DifferentiationPlatformService(repository, new ObjectMapper());
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createCopilotRecommendation_requiresHumanApprovalWhenPolicyRiskIsHigh() {
        when(repository.insert(anyString(), any(Map.class), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        DifferentiationDto.CopilotRecommendationRequest request = new DifferentiationDto.CopilotRecommendationRequest();
        request.setArtifactType("campaign");
        request.setObjective("increase conversions for commercial launch");
        request.setRequireHumanApproval(false);
        request.setPolicyContext(Map.of(
                "commercial", true,
                "consentCoveragePercent", 82,
                "usesSensitiveData", true,
                "estimatedRecipients", 150_000
        ));
        request.setCandidateContent(Map.of("subject", "Limited offer"));
        request.setConstraints(List.of("brand approval required"));

        Map<String, Object> saved = service.createCopilotRecommendation(request);

        assertThat(saved.get("tenant_id")).isEqualTo("tenant-1");
        assertThat(saved.get("workspace_id")).isEqualTo("workspace-1");
        assertThat(saved.get("status")).isEqualTo("PENDING_APPROVAL");
        assertThat(saved.get("approval_required")).isEqualTo(true);
        assertThat((Integer) saved.get("risk_score")).isGreaterThanOrEqualTo(70);
        assertThat((String) saved.get("recommendations")).contains("DELIVERABILITY_RAMP");
    }

    @Test
    void evaluateDecisionPolicy_selectsBestEligibleVariantAndRecordsEvent() {
        when(repository.queryForList(anyString(), any(Map.class))).thenReturn(List.of(Map.of(
                "id", "policy-1",
                "workspace_id", "workspace-1",
                "policy_key", "next-best-offer",
                "trigger_event", "PROFILE_UPDATED",
                "variants", List.of(
                        Map.of("key", "generic", "channel", "EMAIL", "weight", 10),
                        Map.of("key", "upgrade", "channel", "EMAIL", "weight", 50, "tag", "upgrade")
                ),
                "guardrails", Map.of("blockedChannels", List.of("SMS"))
        )));
        when(repository.insert(anyString(), any(Map.class), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        DifferentiationDto.DecisionEvaluateRequest request = new DifferentiationDto.DecisionEvaluateRequest();
        request.setPolicyKey("next-best-offer");
        request.setChannel("EMAIL");
        request.setProfileUpdates(Map.of("subjectId", "sub-1", "interest", "upgrade", "consent", "OPT_IN"));

        Map<String, Object> decision = service.evaluateDecisionPolicy(request);

        assertThat(decision.get("eligible")).isEqualTo(true);
        assertThat((Double) decision.get("confidence")).isGreaterThan(0.6);
        Map<?, ?> selected = (Map<?, ?>) decision.get("selectedVariant");
        assertThat(selected.get("key")).isEqualTo("upgrade");
    }

    @Test
    void evaluateSloPolicy_opensIncidentAndTriggersSelfHealingWhenBurnExceeded() {
        when(repository.queryForList(anyString(), any(Map.class))).thenReturn(List.of(Map.of(
                "id", "slo-1",
                "workspace_id", "workspace-1",
                "service_name", "delivery-service",
                "slo_target_percent", BigDecimal.valueOf(99.9),
                "synthetic_probe", Map.of("p95LatencyMs", 1200),
                "self_healing_actions", List.of(Map.of("action", "scale_workers")),
                "capacity_forecast", Map.of("unit", "workers")
        )));
        when(repository.insert(anyString(), any(Map.class), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        DifferentiationDto.SloEvaluateRequest request = new DifferentiationDto.SloEvaluateRequest();
        request.setServiceName("delivery-service");
        request.setSuccessRatePercent(98.9);
        request.setP95LatencyMs(1800L);
        request.setSaturationPercent(94.0);
        request.setQueueDepth(12_000L);

        Map<String, Object> result = service.evaluateSloPolicy(request);

        assertThat(result.get("incidentStatus")).isEqualTo("ACTIVE");
        assertThat(result.get("selfHealingTriggered")).isEqualTo(true);
        assertThat((Double) result.get("errorBudgetBurnPercent")).isGreaterThan(100);
        assertThat((List<?>) result.get("recommendedActions")).isNotEmpty();
    }
}

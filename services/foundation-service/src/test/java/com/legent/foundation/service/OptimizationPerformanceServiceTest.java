package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.OptimizationEvaluateRequest;
import com.legent.foundation.dto.performance.OptimizationPolicyRequest;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.foundation.service.performance.ClosedLoopOptimizationService;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(revenuePolicy()));
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

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
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(revenuePolicy()));
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

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

    @Test
    void upsertPolicy_acceptsSendTimeOptimizationPolicy() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of());
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        OptimizationPolicyRequest request = new OptimizationPolicyRequest();
        request.setPolicyKey("sto-commercial");
        request.setName("Commercial STO readiness");
        request.setOptimizationType("send_time");
        request.setGuardrails(Map.of(
                "minEligibleEngagementEvents", 1000,
                "minEligibleContacts", 500,
                "lookbackDays", 90
        ));

        Map<String, Object> saved = service.upsertPolicy(request);

        assertThat(saved.get("optimization_type")).isEqualTo("SEND_TIME");
        assertThat(saved.get("policy_key")).isEqualTo("sto-commercial");
        verify(repository).insert(ArgumentMatchers.eq("performance_optimization_policies"), ArgumentMatchers.<Map<String, Object>>any(), anyList());
    }

    @Test
    void upsertPolicy_acceptsFrequencyOptimizationPolicy() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of());
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        OptimizationPolicyRequest request = new OptimizationPolicyRequest();
        request.setPolicyKey("frequency-commercial");
        request.setName("Commercial frequency readiness");
        request.setOptimizationType("frequency");
        request.setGuardrails(Map.of(
                "minEligibleSendEvents", 1000,
                "minEligibleContacts", 500,
                "minFrequencyVariants", 5,
                "lookbackDays", 28
        ));

        Map<String, Object> saved = service.upsertPolicy(request);

        assertThat(saved.get("optimization_type")).isEqualTo("FREQUENCY");
        assertThat(saved.get("policy_key")).isEqualTo("frequency-commercial");
    }

    @Test
    void upsertPolicy_requiresWorkspaceContextEvenWhenRequestCarriesWorkspace() {
        TenantContext.setWorkspaceId(null);

        OptimizationPolicyRequest request = new OptimizationPolicyRequest();
        request.setWorkspaceId("workspace-1");
        request.setPolicyKey("sto-commercial");
        request.setName("Commercial STO readiness");
        request.setOptimizationType("send_time");

        assertThatThrownBy(() -> service.upsertPolicy(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId is required");
        verifyNoInteractions(repository);
    }

    @Test
    void listPolicies_usesExactWorkspaceScope() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of());

        service.listPolicies(null);

        verify(repository).queryForList(
                ArgumentMatchers.argThat(sql -> sql.contains("workspace_id = :workspaceId")
                        && !sql.contains(":workspaceId IS NULL")),
                ArgumentMatchers.<Map<String, Object>>argThat(params -> "workspace-1".equals(params.get("workspaceId")))
        );
    }

    @Test
    void evaluate_sendTimeLowDataFallsBackWithoutScheduleChange() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(sendTimePolicy()));
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        OptimizationEvaluateRequest request = new OptimizationEvaluateRequest();
        request.setPolicyKey("sto-commercial");
        request.setSignals(Map.of(
                "consentCoveragePercent", 99,
                "sendClassification", "COMMERCIAL",
                "eligibleEngagementEvents", 250,
                "eligibleContacts", 100,
                "engagementWindowCoveragePercent", 40,
                "changesLaunchTiming", false
        ));

        Map<String, Object> result = service.evaluate(request);

        assertThat(result.get("optimizationType")).isEqualTo("SEND_TIME");
        assertThat(result.get("fallbackMode")).isEqualTo("LOW_DATA_DEFAULT_SCHEDULE");
        assertThat(result.get("confidenceBand")).isEqualTo("LOW");
        assertThat(result.get("approvalRequired")).isEqualTo(false);
        assertThat((List<?>) result.get("dataQualityReasons")).isNotEmpty();
        assertThat((List<?>) result.get("recommendations")).anySatisfy(item ->
                assertThat(((Map<?, ?>) item).get("key")).isEqualTo("send_time.fallback"));
    }

    @Test
    void evaluate_sendTimeBlocksWhenSafetySignalsFail() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(sendTimePolicy()));
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        OptimizationEvaluateRequest request = new OptimizationEvaluateRequest();
        request.setPolicyKey("sto-commercial");
        request.setSignals(Map.ofEntries(
                Map.entry("consentCoveragePercent", 99),
                Map.entry("sendClassification", "COMMERCIAL"),
                Map.entry("eligibleEngagementEvents", 5000),
                Map.entry("eligibleContacts", 3000),
                Map.entry("engagementWindowCoveragePercent", 90),
                Map.entry("changesLaunchTiming", true),
                Map.entry("quietHoursGatePassed", false),
                Map.entry("approvalGatePassed", true),
                Map.entry("suppressionGatePassed", true),
                Map.entry("warmupGatePassed", true),
                Map.entry("rateLimitGatePassed", true),
                Map.entry("providerCapacityGatePassed", true),
                Map.entry("deliverabilityGatePassed", false)
        ));

        Map<String, Object> result = service.evaluate(request);

        assertThat(result.get("approvalRequired")).isEqualTo(true);
        assertThat(result.get("rollbackRequired")).isEqualTo(true);
        assertThat((List<?>) result.get("blockedReasons")).anySatisfy(reason ->
                assertThat(String.valueOf(reason)).contains("Quiet-hours"));
        assertThat((List<?>) result.get("blockedReasons")).anySatisfy(reason ->
                assertThat(String.valueOf(reason)).contains("Deliverability"));
        assertThat(result.get("confidenceBand")).isEqualTo("LOW");
    }

    @Test
    void evaluate_sendTimeRequiresApprovalWhenRecommendationChangesLaunchTiming() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(sendTimePolicy()));
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        OptimizationEvaluateRequest request = new OptimizationEvaluateRequest();
        request.setPolicyKey("sto-commercial");
        request.setSignals(Map.ofEntries(
                Map.entry("consentCoveragePercent", 99),
                Map.entry("sendClassification", "COMMERCIAL"),
                Map.entry("eligibleEngagementEvents", 5000),
                Map.entry("eligibleContacts", 3000),
                Map.entry("engagementWindowCoveragePercent", 90),
                Map.entry("changesLaunchTiming", true),
                Map.entry("quietHoursGatePassed", true),
                Map.entry("approvalGatePassed", true),
                Map.entry("suppressionGatePassed", true),
                Map.entry("warmupGatePassed", true),
                Map.entry("rateLimitGatePassed", true),
                Map.entry("providerCapacityGatePassed", true),
                Map.entry("deliverabilityGatePassed", true)
        ));

        Map<String, Object> result = service.evaluate(request);

        assertThat(result.get("approvalRequired")).isEqualTo(true);
        assertThat(result.get("rollbackRequired")).isEqualTo(true);
        assertThat((List<?>) result.get("blockedReasons")).isEmpty();
        assertThat(result.get("confidenceBand")).isEqualTo("HIGH");
        assertThat(result.get("fallbackMode")).isEqualTo("NONE");
    }

    @Test
    void evaluate_sendTimeSeparatesCommercialAndTransactionalDataByPolicy() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(sendTimePolicy()));
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        OptimizationEvaluateRequest request = new OptimizationEvaluateRequest();
        request.setPolicyKey("sto-commercial");
        request.setSignals(Map.of(
                "consentCoveragePercent", 99,
                "sendClassification", "COMMERCIAL",
                "usesTransactionalEngagementData", true,
                "eligibleEngagementEvents", 5000,
                "eligibleContacts", 3000,
                "engagementWindowCoveragePercent", 90
        ));

        Map<String, Object> result = service.evaluate(request);

        assertThat((List<?>) result.get("blockedReasons")).anySatisfy(reason ->
                assertThat(String.valueOf(reason)).contains("Commercial STO cannot use transactional"));
        assertThat(result.get("approvalRequired")).isEqualTo(true);
    }

    @Test
    void evaluate_frequencyLowDataFallsBackToCurrentCap() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(frequencyPolicy()));
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        OptimizationEvaluateRequest request = new OptimizationEvaluateRequest();
        request.setPolicyKey("frequency-commercial");
        request.setSignals(Map.of(
                "consentCoveragePercent", 99,
                "eligibleSendEvents", 200,
                "eligibleContacts", 100,
                "frequencyVariantCount", 2,
                "currentFrequencyCap", 3,
                "requestedFrequencyCap", 3,
                "changesCadence", false
        ));

        Map<String, Object> result = service.evaluate(request);

        assertThat(result.get("optimizationType")).isEqualTo("FREQUENCY");
        assertThat(result.get("fallbackMode")).isEqualTo("LOW_DATA_CURRENT_CAP");
        assertThat(result.get("confidenceBand")).isEqualTo("LOW");
        assertThat(result.get("recommendedCap")).isEqualTo(3);
        assertThat(result.get("minimumVariantsMet")).isEqualTo(false);
        assertThat(result.get("approvalRequired")).isEqualTo(false);
        assertThat((List<?>) result.get("dataQualityReasons")).isNotEmpty();
    }

    @Test
    void evaluate_frequencyBlocksUnsafeCadenceIncrease() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(frequencyPolicy()));
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        OptimizationEvaluateRequest request = new OptimizationEvaluateRequest();
        request.setPolicyKey("frequency-commercial");
        request.setSignals(Map.ofEntries(
                Map.entry("consentCoveragePercent", 99),
                Map.entry("eligibleSendEvents", 5_000),
                Map.entry("eligibleContacts", 3_000),
                Map.entry("frequencyVariantCount", 5),
                Map.entry("currentFrequencyCap", 3),
                Map.entry("requestedFrequencyCap", 4),
                Map.entry("cadenceIncreaseRequested", true),
                Map.entry("suppressed", true),
                Map.entry("complaintProne", true),
                Map.entry("suppressionGatePassed", false),
                Map.entry("unsubscribeGatePassed", true),
                Map.entry("warmupGatePassed", true),
                Map.entry("rateLimitGatePassed", true),
                Map.entry("providerCapacityGatePassed", false),
                Map.entry("deliverabilityGatePassed", false),
                Map.entry("frequencyCapGatePassed", false)
        ));

        Map<String, Object> result = service.evaluate(request);

        assertThat(result.get("approvalRequired")).isEqualTo(true);
        assertThat(result.get("rollbackRequired")).isEqualTo(true);
        assertThat(result.get("recommendedCap")).isEqualTo(3);
        assertThat((List<?>) result.get("blockedReasons")).anySatisfy(reason ->
                assertThat(String.valueOf(reason)).contains("Suppressed recipients"));
        assertThat((List<?>) result.get("blockedReasons")).anySatisfy(reason ->
                assertThat(String.valueOf(reason)).contains("Provider-capacity"));
        assertThat((List<?>) result.get("safetyImpact")).isNotEmpty();
    }

    @Test
    void evaluate_frequencyCadenceIncreaseRequiresApprovalAndRollback() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(frequencyPolicy()));
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        OptimizationEvaluateRequest request = new OptimizationEvaluateRequest();
        request.setPolicyKey("frequency-commercial");
        request.setSignals(Map.ofEntries(
                Map.entry("consentCoveragePercent", 99),
                Map.entry("eligibleSendEvents", 5_000),
                Map.entry("eligibleContacts", 3_000),
                Map.entry("frequencyVariantCount", 5),
                Map.entry("currentFrequencyCap", 3),
                Map.entry("requestedFrequencyCap", 4),
                Map.entry("changesCadence", true),
                Map.entry("suppressionGatePassed", true),
                Map.entry("unsubscribeGatePassed", true),
                Map.entry("warmupGatePassed", true),
                Map.entry("rateLimitGatePassed", true),
                Map.entry("providerCapacityGatePassed", true),
                Map.entry("deliverabilityGatePassed", true),
                Map.entry("frequencyCapGatePassed", true)
        ));

        Map<String, Object> result = service.evaluate(request);

        assertThat((List<?>) result.get("blockedReasons")).isEmpty();
        assertThat(result.get("approvalRequired")).isEqualTo(true);
        assertThat(result.get("rollbackRequired")).isEqualTo(true);
        assertThat(result.get("confidenceBand")).isEqualTo("HIGH");
        assertThat(result.get("recommendedCap")).isEqualTo(4);
        assertThat(result.get("saturationCategory")).isEqualTo("LOW");
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

    private Map<String, Object> sendTimePolicy() {
        return Map.of(
                "id", "policy-sto",
                "workspace_id", "workspace-1",
                "policy_key", "sto-commercial",
                "optimization_type", "SEND_TIME",
                "guardrails", Map.of(
                        "minConsentCoveragePercent", 95,
                        "minEligibleEngagementEvents", 1000,
                        "minEligibleContacts", 500,
                        "minEngagementWindowCoveragePercent", 60,
                        "lookbackDays", 90,
                        "allowTransactionalDataForCommercial", false,
                        "allowTransactionalSendTimePolicy", false
                ),
                "approval_policy", Map.of("requireHumanApproval", false),
                "rollback_policy", Map.of("snapshotRequired", false)
        );
    }

    private Map<String, Object> frequencyPolicy() {
        return Map.of(
                "id", "policy-frequency",
                "workspace_id", "workspace-1",
                "policy_key", "frequency-commercial",
                "optimization_type", "FREQUENCY",
                "guardrails", Map.of(
                        "minConsentCoveragePercent", 95,
                        "minEligibleSendEvents", 1000,
                        "minEligibleContacts", 500,
                        "minFrequencyVariants", 5,
                        "lookbackDays", 28
                ),
                "approval_policy", Map.of("requireHumanApproval", false),
                "rollback_policy", Map.of("snapshotRequired", false)
        );
    }
}

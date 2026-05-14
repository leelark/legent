package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.GlobalEnterpriseDto;
import com.legent.foundation.repository.CorePlatformRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalEnterpriseServiceTest {

    @Mock
    private CorePlatformRepository repository;

    private GlobalEnterpriseService service;

    @BeforeEach
    void setUp() {
        service = new GlobalEnterpriseService(repository, new ObjectMapper());
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void evaluateFailover_blocksWhenResidencyPolicyMissing() {
        when(repository.queryForList(anyString(), any(Map.class))).thenReturn(List.of(model()), List.of());

        GlobalEnterpriseDto.FailoverEvaluationRequest request = new GlobalEnterpriseDto.FailoverEvaluationRequest();
        request.setDataClass("CONTACT");
        request.setSourceRegion("us-east-1");
        request.setTargetRegion("eu-west-1");
        request.setOperatingModelKey("global-email");

        Map<String, Object> result = service.evaluateFailover(request);

        assertThat(result.get("allowed")).isEqualTo(false);
        assertThat(result.get("decision")).isEqualTo("BLOCK");
        assertThat((List<?>) result.get("reasons")).anySatisfy(reason ->
                assertThat(String.valueOf(reason)).contains("No active data residency policy"));
    }

    @Test
    void evaluateFailover_allowsActiveActiveWhenTopologyAndResidencyPermitTarget() {
        when(repository.queryForList(anyString(), any(Map.class))).thenReturn(List.of(model()), List.of(residencyPolicy()));

        GlobalEnterpriseDto.FailoverEvaluationRequest request = new GlobalEnterpriseDto.FailoverEvaluationRequest();
        request.setDataClass("CONTACT");
        request.setSourceRegion("us-east-1");
        request.setTargetRegion("eu-west-1");
        request.setTopologyMode("ACTIVE_ACTIVE");
        request.setOperatingModelKey("global-email");

        Map<String, Object> result = service.evaluateFailover(request);

        assertThat(result.get("allowed")).isEqualTo(true);
        assertThat(result.get("decision")).isEqualTo("ALLOW");
        assertThat(result.get("rpoTargetMinutes")).isEqualTo(5);
    }

    @Test
    void createFailoverDrill_failsWhenActualRtoBreachesTarget() {
        when(repository.queryForList(anyString(), any(Map.class))).thenReturn(List.of(model()));
        when(repository.insert(anyString(), any(Map.class), anyList())).thenAnswer(invocation -> invocation.getArgument(1));
        when(repository.updateByIdAndWorkspace(anyString(), anyString(), anyString(), anyString(), any(Map.class), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(4));

        GlobalEnterpriseDto.FailoverDrillRequest request = new GlobalEnterpriseDto.FailoverDrillRequest();
        request.setOperatingModelId("model-1");
        request.setSourceRegion("us-east-1");
        request.setTargetRegion("eu-west-1");
        request.setActualRpoMinutes(4);
        request.setActualRtoMinutes(45);
        request.setAffectedServices(List.of("identity-service", "delivery-service"));

        Map<String, Object> saved = service.createFailoverDrill(request);

        assertThat(saved.get("verdict")).isEqualTo("FAIL");
        assertThat(saved.get("planned_rto_minutes")).isEqualTo(30);
    }

    @Test
    void upsertEncryptionPolicy_rejectsInvalidRotationWindow() {
        GlobalEnterpriseDto.EncryptionPolicyRequest request = new GlobalEnterpriseDto.EncryptionPolicyRequest();
        request.setPolicyKey("contact-eu");
        request.setDataClass("CONTACT");
        request.setKeyProvider("KMS");
        request.setKeyRef("secret/key");
        request.setRotationDays(0);

        assertThatThrownBy(() -> service.upsertEncryptionPolicy(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rotationDays");
    }

    @Test
    void runPolicySimulation_blocksLegalHoldAndResidencyViolations() {
        when(repository.insert(anyString(), any(Map.class), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        GlobalEnterpriseDto.PolicySimulationRequest request = new GlobalEnterpriseDto.PolicySimulationRequest();
        request.setSimulationKey("sim-1");
        request.setPolicyType("OPTIMIZATION");
        request.setArtifactType("CAMPAIGN");
        request.setArtifactId("campaign-1");
        request.setInputContext(Map.of("legalHoldActive", true, "residencyViolation", true));

        Map<String, Object> saved = service.runPolicySimulation(request);

        assertThat(saved.get("verdict")).isEqualTo("BLOCK");
        assertThat((String) saved.get("findings")).contains("LEGAL_HOLD", "DATA_RESIDENCY");
    }

    @Test
    void upsertConnectorInstance_blocksUnsupportedAuthMode() {
        when(repository.queryForList(anyString(), any(Map.class))).thenReturn(List.of(Map.of(
                "id", "template-1",
                "connector_key", "salesforce-crm",
                "auth_modes", List.of("OAUTH2")
        )));

        GlobalEnterpriseDto.ConnectorInstanceRequest request = new GlobalEnterpriseDto.ConnectorInstanceRequest();
        request.setTemplateId("template-1");
        request.setInstanceKey("sf-prod");
        request.setConnectorKey("salesforce-crm");
        request.setDisplayName("Salesforce prod");
        request.setCategory("CRM");
        request.setAuthMode("API_KEY");

        assertThatThrownBy(() -> service.upsertConnectorInstance(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported connector auth mode");
    }

    @Test
    void optimizationRecommendation_autoAppliesOnlyWhenGuardrailsPassThenRollbackUsesSnapshot() {
        when(repository.queryForList(anyString(), any(Map.class)))
                .thenReturn(List.of(Map.of(
                        "id", "policy-1",
                        "policy_key", "auto-subject",
                        "mode", "AUTO_APPLY_WITH_GUARDRAILS",
                        "constraints", Map.of("brand", "strict")
                )))
                .thenReturn(List.of(Map.of(
                        "id", "rec-1",
                        "workspace_id", "workspace-1",
                        "rollback_snapshot", Map.of("subject", "Old subject")
                )));
        when(repository.insert(anyString(), any(Map.class), anyList())).thenAnswer(invocation -> invocation.getArgument(1));
        when(repository.updateByIdAndWorkspace(anyString(), anyString(), anyString(), anyString(), any(Map.class), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(4));

        GlobalEnterpriseDto.OptimizationRecommendationRequest request = new GlobalEnterpriseDto.OptimizationRecommendationRequest();
        request.setPolicyKey("auto-subject");
        request.setArtifactType("CAMPAIGN");
        request.setArtifactId("campaign-1");
        request.setInputSignals(Map.of(
                "policySimulationPassed", true,
                "brandCompliant", true,
                "complianceCompliant", true,
                "riskScore", 5
        ));
        request.setRecommendation(Map.of("subject", "New subject", "brandCompliant", true, "complianceCompliant", true));
        request.setTargetSnapshot(Map.of("subject", "Old subject"));

        Map<String, Object> saved = service.createOptimizationRecommendation(request);

        assertThat(saved.get("status")).isEqualTo("APPLIED");
        assertThat(saved.get("decision_by")).isEqualTo("SYSTEM");

        GlobalEnterpriseDto.OptimizationRollbackRequest rollbackRequest = new GlobalEnterpriseDto.OptimizationRollbackRequest();
        rollbackRequest.setRecommendationId("rec-1");
        rollbackRequest.setReason("Metric regression");
        rollbackRequest.setEvidence(Map.of("metric", "ctr"));

        Map<String, Object> rollback = service.createOptimizationRollback(rollbackRequest);

        assertThat(rollback.get("status")).isEqualTo("COMPLETED");
        assertThat((String) rollback.get("rollback_snapshot")).contains("Old subject");
    }

    private Map<String, Object> model() {
        return Map.of(
                "id", "model-1",
                "workspace_id", "workspace-1",
                "topology_mode", "ACTIVE_ACTIVE",
                "primary_region", "us-east-1",
                "standby_regions", List.of("eu-west-1"),
                "active_regions", List.of("us-east-1", "eu-west-1"),
                "rpo_target_minutes", 5,
                "rto_target_minutes", 30
        );
    }

    private Map<String, Object> residencyPolicy() {
        return Map.of(
                "id", "residency-1",
                "data_class", "CONTACT",
                "home_region", "us-east-1",
                "allowed_regions", List.of("us-east-1", "eu-west-1"),
                "blocked_regions", List.of(),
                "failover_allowed", true
        );
    }
}

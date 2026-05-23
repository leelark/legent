package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.GlobalEnterpriseDto;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
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
    void listOperatingModels_failsClosedWhenWorkspaceContextMissing() {
        TenantContext.setWorkspaceId(null);

        assertThatThrownBy(() -> service.listOperatingModels(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workspace context is not set");
        verifyNoInteractions(repository);
    }

    @Test
    void upsertOperatingModel_rejectsRequestWorkspaceMismatchBeforeRepositoryAccess() {
        GlobalEnterpriseDto.OperatingModelRequest request = operatingModelRequest();
        request.setWorkspaceId("workspace-2");

        assertThatThrownBy(() -> service.upsertOperatingModel(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId does not match the current workspace");
        verifyNoInteractions(repository);
    }

    @Test
    void upsertOperatingModel_usesExactWorkspacePredicate() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.<Map<String, Object>>of());
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        Map<String, Object> saved = service.upsertOperatingModel(operatingModelRequest());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(repository).queryForList(sqlCaptor.capture(), paramsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("workspace_id = :workspaceId")
                .doesNotContain("COALESCE(workspace_id")
                .doesNotContain(":workspaceId IS NULL");
        assertThat(paramsCaptor.getValue()).containsEntry("workspaceId", "workspace-1");
        assertThat(saved).containsEntry("workspace_id", "workspace-1");
    }

    @Test
    void listFailoverDrills_usesExactWorkspacePredicate() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.<Map<String, Object>>of());

        service.listFailoverDrills(null, 100);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(repository).queryForList(sqlCaptor.capture(), paramsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("workspace_id = :workspaceId")
                .doesNotContain(":workspaceId IS NULL");
        assertThat(paramsCaptor.getValue()).containsEntry("workspaceId", "workspace-1");
    }

    @Test
    void evaluateFailover_blocksWhenResidencyPolicyMissing() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(model()), List.<Map<String, Object>>of());

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
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(model()), List.of(residencyPolicy()));

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
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(model()));
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));
        when(repository.updateByIdAndWorkspace(anyString(), anyString(), anyString(), anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList()))
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
    void createFailoverDrill_failsClosedWhenNoActiveOperatingModelExists() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.<Map<String, Object>>of());

        GlobalEnterpriseDto.FailoverDrillRequest request = new GlobalEnterpriseDto.FailoverDrillRequest();
        request.setSourceRegion("us-east-1");
        request.setTargetRegion("eu-west-1");
        request.setActualRpoMinutes(4);
        request.setActualRtoMinutes(20);

        assertThatThrownBy(() -> service.createFailoverDrill(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Active operating model is required");
        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList());
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
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

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
    void upsertConnectorTemplate_remainsTenantLevelWithoutWorkspaceContext() {
        TenantContext.setWorkspaceId(null);
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.<Map<String, Object>>of());
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        GlobalEnterpriseDto.ConnectorTemplateRequest request = connectorTemplateRequest();

        Map<String, Object> saved = service.upsertConnectorTemplate(request);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(repository).queryForList(sqlCaptor.capture(), paramsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("WHERE tenant_id = :tenantId")
                .doesNotContain("workspace_id")
                .doesNotContain(":workspaceId");
        assertThat(paramsCaptor.getValue()).containsEntry("tenantId", "tenant-1");
        assertThat(saved).containsEntry("workspace_id", null);
    }

    @Test
    void upsertConnectorTemplate_allowsMarketplaceTemplateTableAndConnectorKey() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.<Map<String, Object>>of());
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        GlobalEnterpriseDto.ConnectorTemplateRequest request = connectorTemplateRequest();

        Map<String, Object> saved = service.upsertConnectorTemplate(request);

        assertThat(saved.get("connector_key")).isEqualTo("salesforce-crm");
        assertThat(saved.get("auth_modes")).isEqualTo("[\"OAUTH2\"]");
    }

    @Test
    void upsertConnectorInstance_blocksMissingTemplate() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.<Map<String, Object>>of());

        GlobalEnterpriseDto.ConnectorInstanceRequest request = connectorInstanceRequest();

        assertThatThrownBy(() -> service.upsertConnectorInstance(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Connector template not found");
        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList());
    }

    @Test
    void upsertConnectorInstance_blocksMismatchedTemplateConnectorKey() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(Map.of(
                "id", "template-1",
                "connector_key", "segment-cdp",
                "auth_modes", List.of("OAUTH2")
        )));

        GlobalEnterpriseDto.ConnectorInstanceRequest request = connectorInstanceRequest();
        request.setTemplateId("template-1");

        assertThatThrownBy(() -> service.upsertConnectorInstance(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match connector key");
        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList());
    }

    @Test
    void upsertConnectorInstance_savesMatchingTemplateAndWorkspaceScope() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(Map.of(
                        "id", "template-1",
                        "connector_key", "salesforce-crm",
                        "auth_modes", List.of("OAUTH2")
                )))
                .thenReturn(List.<Map<String, Object>>of());
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));

        GlobalEnterpriseDto.ConnectorInstanceRequest request = connectorInstanceRequest();

        Map<String, Object> saved = service.upsertConnectorInstance(request);

        assertThat(saved)
                .containsEntry("template_id", "template-1")
                .containsEntry("connector_key", "salesforce-crm")
                .containsEntry("workspace_id", "workspace-1")
                .containsEntry("instance_key", "sf-prod");
        verify(repository).insert("marketplace_connector_instances", saved, List.of("config", "validation_result"));
    }

    @Test
    void upsertConnectorInstance_blocksUnsupportedAuthMode() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(Map.of(
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
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
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
        when(repository.insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList())).thenAnswer(invocation -> invocation.getArgument(1));
        when(repository.updateByIdAndWorkspace(anyString(), anyString(), anyString(), anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList()))
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

    private GlobalEnterpriseDto.OperatingModelRequest operatingModelRequest() {
        GlobalEnterpriseDto.OperatingModelRequest request = new GlobalEnterpriseDto.OperatingModelRequest();
        request.setModelKey("global-email");
        request.setName("Global email operating model");
        request.setTopologyMode("ACTIVE_WARM");
        request.setPrimaryRegion("us-east-1");
        request.setStandbyRegions(List.of("eu-west-1"));
        return request;
    }

    private GlobalEnterpriseDto.ConnectorTemplateRequest connectorTemplateRequest() {
        GlobalEnterpriseDto.ConnectorTemplateRequest request = new GlobalEnterpriseDto.ConnectorTemplateRequest();
        request.setConnectorKey("salesforce-crm");
        request.setCategory("CRM");
        request.setDisplayName("Salesforce CRM");
        request.setVendor("Salesforce");
        request.setAuthModes(List.of("OAUTH2"));
        request.setSupportedEvents(List.of("contact.updated"));
        request.setCapabilities(Map.of("objects", List.of("Contact")));
        return request;
    }

    private GlobalEnterpriseDto.ConnectorInstanceRequest connectorInstanceRequest() {
        GlobalEnterpriseDto.ConnectorInstanceRequest request = new GlobalEnterpriseDto.ConnectorInstanceRequest();
        request.setTemplateId("template-1");
        request.setInstanceKey("sf-prod");
        request.setConnectorKey("salesforce-crm");
        request.setDisplayName("Salesforce prod");
        request.setCategory("CRM");
        request.setAuthMode("OAUTH2");
        return request;
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

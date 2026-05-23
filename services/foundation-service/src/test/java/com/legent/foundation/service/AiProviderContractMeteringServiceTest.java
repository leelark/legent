package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.AiProviderContractRequest;
import com.legent.foundation.dto.performance.AiProviderMeteringRequest;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.foundation.service.performance.AiProviderContractMeteringService;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProviderContractMeteringServiceTest {

    @Mock
    private CorePlatformRepository repository;

    private AiProviderContractMeteringService service;

    @BeforeEach
    void setUp() {
        service = new AiProviderContractMeteringService(repository, new ObjectMapper());
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void upsertContract_storesProviderDisclosureMeteringAndKillSwitchPolicy() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of());
        when(repository.insert(eq("ai_provider_contracts"), ArgumentMatchers.<Map<String, Object>>any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        AiProviderContractRequest request = contractRequest();
        request.setKillSwitchEnabled(true);
        request.setMaxUnitsPerRequest(new BigDecimal("2500"));
        request.setRetentionPolicy(Map.<String, Object>of("retentionDays", 30));

        Map<String, Object> saved = service.upsertContract(request);

        assertThat(saved.get("workspace_id")).isEqualTo("workspace-1");
        assertThat(saved.get("contract_key")).isEqualTo("openai-content");
        assertThat(saved.get("provider_key")).isEqualTo("openai");
        assertThat(saved.get("provider_disclosure").toString()).contains("OpenAI", "gpt-content-safe");
        assertThat(saved.get("training_usage_allowed")).isEqualTo(false);
        assertThat(saved.get("metering_enabled")).isEqualTo(true);
        assertThat(saved.get("kill_switch_enabled")).isEqualTo(true);
        assertThat(saved.get("max_units_per_request")).isEqualTo(new BigDecimal("2500"));
        assertThat(saved.get("prompt_storage_policy")).isEqualTo("HASH_ONLY");
        assertThat(saved.get("output_storage_policy")).isEqualTo("HASH_ONLY");
    }

    @Test
    void upsertContract_rejectsRawPromptOrOutputStorage() {
        AiProviderContractRequest request = contractRequest();
        request.setOutputStoragePolicy("RAW");

        assertThatThrownBy(() -> service.upsertContract(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hashes only");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList());
    }

    @Test
    void evaluateMetering_deniesMissingWorkspaceBeforeRepositoryAccess() {
        TenantContext.setWorkspaceId(null);
        AiProviderMeteringRequest request = meteringRequest();

        assertThatThrownBy(() -> service.evaluateMetering(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId is required");

        verify(repository, never()).queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any());
        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList());
    }

    @Test
    void evaluateMetering_deniesMissingContractBeforeMeteringEventInsert() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.evaluateMetering(meteringRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI provider contract not found");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList());
    }

    @Test
    void evaluateMetering_deniesIncompleteDisclosureAndKillSwitchWithoutProviderInvocation() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(contractRow(true, Map.of())));
        when(repository.insert(eq("ai_provider_metering_events"), ArgumentMatchers.<Map<String, Object>>any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        Map<String, Object> result = service.evaluateMetering(meteringRequest());

        assertThat(result.get("decision")).isEqualTo("DENIED");
        assertThat((List<?>) result.get("blockedReasons")).anySatisfy(reason ->
                assertThat(String.valueOf(reason)).contains("kill switch"));
        assertThat((List<?>) result.get("blockedReasons")).anySatisfy(reason ->
                assertThat(String.valueOf(reason)).contains("disclosure"));
        assertThat(result.get("providerInvoked")).isEqualTo(false);

        ArgumentCaptor<Map<String, Object>> values = ArgumentCaptor.forClass(Map.class);
        verify(repository).insert(eq("ai_provider_metering_events"), values.capture(), anyList());
        Map<String, Object> saved = values.getValue();
        assertThat(saved.get("decision")).isEqualTo("DENIED");
        assertThat(saved.get("provider_invoked")).isEqualTo(false);
        assertThat(saved.get("raw_prompt_stored")).isEqualTo(false);
        assertThat(saved.get("raw_output_stored")).isEqualTo(false);
    }

    @Test
    void evaluateMetering_deniesWhenProviderDisclosureWasNotAccepted() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(contractRow(false, providerDisclosure())));
        when(repository.insert(eq("ai_provider_metering_events"), ArgumentMatchers.<Map<String, Object>>any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        AiProviderMeteringRequest request = meteringRequest();
        request.setDisclosureAccepted(false);

        Map<String, Object> result = service.evaluateMetering(request);

        assertThat(result.get("decision")).isEqualTo("DENIED");
        assertThat((List<?>) result.get("blockedReasons")).anySatisfy(reason ->
                assertThat(String.valueOf(reason)).contains("disclosure"));
        assertThat(result.get("modelInvocation")).isEqualTo("NOT_PERFORMED");
    }

    @Test
    void evaluateMetering_approvesMeteredRequestAndSanitizesContext() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(contractRow(false, providerDisclosure())));
        when(repository.insert(eq("ai_provider_metering_events"), ArgumentMatchers.<Map<String, Object>>any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        AiProviderMeteringRequest request = meteringRequest();
        request.setContext(Map.<String, Object>of("apiToken", "SECRET-VALUE", "campaignType", "newsletter"));
        request.setCostEstimate(new BigDecimal("0.120000"));

        Map<String, Object> result = service.evaluateMetering(request);

        ArgumentCaptor<Map<String, Object>> values = ArgumentCaptor.forClass(Map.class);
        verify(repository).insert(eq("ai_provider_metering_events"), values.capture(), anyList());
        Map<String, Object> saved = values.getValue();

        assertThat(result.get("decision")).isEqualTo("APPROVED_METERED");
        assertThat(result.get("providerInvoked")).isEqualTo(false);
        assertThat(result.get("modelInvocation")).isEqualTo("NOT_PERFORMED");
        assertThat(saved.get("decision")).isEqualTo("APPROVED_METERED");
        assertThat(saved.get("created_by")).isEqualTo("user-1");
        assertThat(saved.get("units_requested")).isEqualTo(new BigDecimal("42"));
        assertThat(saved.get("cost_estimate")).isEqualTo(new BigDecimal("0.120000"));
        assertThat(saved.get("request_context").toString()).contains("REDACTED");
        assertThat(saved.toString()).doesNotContain("SECRET-VALUE");
        assertThat(saved.get("policy_snapshot").toString()).contains("openai-content", "v2");
    }

    private AiProviderContractRequest contractRequest() {
        AiProviderContractRequest request = new AiProviderContractRequest();
        request.setContractKey("openai-content");
        request.setProviderKey("openai");
        request.setProviderName("OpenAI");
        request.setModelName("gpt-content-safe");
        request.setDeploymentRegion("us");
        request.setAllowedDataClasses(List.of("BRAND_KIT", "CAMPAIGN_OBJECTIVE"));
        request.setProhibitedDataClasses(List.of("RAW_SUBSCRIBER_ATTRIBUTES"));
        request.setTrainingUsageAllowed(false);
        request.setMeteringEnabled(true);
        request.setVersionLabel("v2");
        return request;
    }

    private AiProviderMeteringRequest meteringRequest() {
        AiProviderMeteringRequest request = new AiProviderMeteringRequest();
        request.setContractKey("openai-content");
        request.setProviderKey("openai");
        request.setFeatureKey("template-draft");
        request.setArtifactType("EMAIL_TEMPLATE");
        request.setArtifactId("template-1");
        request.setRequestId("request-1");
        request.setRequestedAction("draft_suggestion");
        request.setUnitsRequested(new BigDecimal("42"));
        request.setRequestedDataClasses(List.of("BRAND_KIT", "CAMPAIGN_OBJECTIVE"));
        request.setDisclosureAccepted(true);
        request.setEvidenceRefs(List.of("policy://ai-provider/openai-content/v2"));
        return request;
    }

    private Map<String, Object> contractRow(boolean killSwitch, Map<String, Object> providerDisclosure) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "contract-1");
        row.put("workspace_id", "workspace-1");
        row.put("contract_key", "openai-content");
        row.put("provider_key", "openai");
        row.put("provider_name", "OpenAI");
        row.put("model_name", "gpt-content-safe");
        row.put("status", "ACTIVE");
        row.put("provider_disclosure", providerDisclosure);
        row.put("allowed_data_classes", List.of("BRAND_KIT", "CAMPAIGN_OBJECTIVE"));
        row.put("prohibited_data_classes", List.of("RAW_SUBSCRIBER_ATTRIBUTES"));
        row.put("metering_enabled", true);
        row.put("kill_switch_enabled", killSwitch);
        row.put("max_units_per_request", BigDecimal.ZERO);
        row.put("version_label", "v2");
        return row;
    }

    private Map<String, Object> providerDisclosure() {
        return Map.of(
                "providerKey", "openai",
                "providerName", "OpenAI",
                "modelName", "gpt-content-safe",
                "providerInvocationAllowed", false
        );
    }
}

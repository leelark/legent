package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.AiContentAssistanceEvaluateRequest;
import com.legent.foundation.dto.performance.AiContentAssistancePolicyRequest;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.foundation.service.performance.AiContentAssistanceGovernanceService;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class AiContentAssistanceGovernanceServiceTest {

    @Mock
    private CorePlatformRepository repository;

    private AiContentAssistanceGovernanceService service;

    @BeforeEach
    void setUp() {
        service = new AiContentAssistanceGovernanceService(repository, new ObjectMapper());
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void upsertPolicy_requiresWorkspaceAndStoresProviderDisclosureTrainingRetentionKillSwitch() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of());
        when(repository.insert(eq("ai_content_assistance_policies"), ArgumentMatchers.<Map<String, Object>>any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        AiContentAssistancePolicyRequest request = policyRequest();
        request.setKillSwitchEnabled(true);
        request.setRetentionPolicy(Map.of("retentionDays", 30, "category", "AI_CONTENT"));

        Map<String, Object> saved = service.upsertPolicy(request);

        assertThat(saved.get("workspace_id")).isEqualTo("workspace-1");
        assertThat(saved.get("provider_disclosure").toString()).contains("INTERNAL_RULES");
        assertThat(saved.get("training_usage_allowed")).isEqualTo(false);
        assertThat(saved.get("retention_policy").toString()).contains("retentionDays");
        assertThat(saved.get("opt_in_required")).isEqualTo(true);
        assertThat(saved.get("opt_out_enabled")).isEqualTo(true);
        assertThat(saved.get("kill_switch_enabled")).isEqualTo(true);
        assertThat(saved.get("draft_only")).isEqualTo(true);
        assertThat(saved.get("require_human_review")).isEqualTo(true);
    }

    @Test
    void upsertPolicy_rejectsRawPromptOrOutputStorage() {
        AiContentAssistancePolicyRequest request = policyRequest();
        request.setPromptStoragePolicy("raw");

        assertThatThrownBy(() -> service.upsertPolicy(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hashes only");

        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList());
    }

    @Test
    void evaluate_deniesMissingWorkspaceBeforeRepositoryAccess() {
        TenantContext.setWorkspaceId(null);
        AiContentAssistanceEvaluateRequest request = evaluateRequest();

        assertThatThrownBy(() -> service.evaluate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId is required");

        verify(repository, never()).queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any());
        verify(repository, never()).insert(anyString(), ArgumentMatchers.<Map<String, Object>>any(), anyList());
    }

    @Test
    void evaluate_rejectsWorkspaceMismatch() {
        AiContentAssistanceEvaluateRequest request = evaluateRequest();
        request.setWorkspaceId("workspace-2");

        assertThatThrownBy(() -> service.evaluate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId does not match");
    }

    @Test
    void evaluate_deniesInactivePolicyOrKillSwitch() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(policyRow("ACTIVE", true)));
        when(repository.insert(eq("ai_content_assistance_audits"), ArgumentMatchers.<Map<String, Object>>any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        Map<String, Object> result = service.evaluate(evaluateRequest());

        assertThat(result.get("decision")).isEqualTo("DENIED");
        assertThat((List<?>) result.get("blockedReasons")).anySatisfy(reason ->
                assertThat(String.valueOf(reason)).contains("kill switch"));
        assertThat(result.get("providerInvoked")).isEqualTo(false);
    }

    @Test
    void evaluate_deniesDisallowedDataClasses() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(policyRow("ACTIVE", false)));
        when(repository.insert(eq("ai_content_assistance_audits"), ArgumentMatchers.<Map<String, Object>>any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        AiContentAssistanceEvaluateRequest request = evaluateRequest();
        request.setRequestedDataClasses(List.of("BRAND_KIT", "SUBSCRIBER_EVENT_HISTORY"));

        Map<String, Object> result = service.evaluate(request);

        assertThat(result.get("decision")).isEqualTo("DENIED");
        assertThat(result.get("dataClassesBlocked").toString()).contains("SUBSCRIBER_EVENT_HISTORY");
        assertThat((List<?>) result.get("blockedReasons")).anySatisfy(reason ->
                assertThat(String.valueOf(reason)).contains("Requested data classes"));
    }

    @Test
    void evaluate_blocksAutoPublishOrSendWithoutHumanReview() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(policyRow("ACTIVE", false)));
        when(repository.insert(eq("ai_content_assistance_audits"), ArgumentMatchers.<Map<String, Object>>any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        AiContentAssistanceEvaluateRequest request = evaluateRequest();
        request.setRequestedAction("auto_publish");
        request.setHumanReviewApproved(false);

        Map<String, Object> result = service.evaluate(request);

        assertThat(result.get("decision")).isEqualTo("DENIED");
        assertThat((List<?>) result.get("blockedReasons")).anySatisfy(reason ->
                assertThat(String.valueOf(reason)).contains("cannot publish, send"));
        assertThat(result.get("providerInvoked")).isEqualTo(false);
    }

    @Test
    void evaluate_storesPolicyVersionActorDataClassesPromptTemplateAndHashesOnly() {
        when(repository.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(policyRow("ACTIVE", false)));
        when(repository.insert(eq("ai_content_assistance_audits"), ArgumentMatchers.<Map<String, Object>>any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        AiContentAssistanceEvaluateRequest request = evaluateRequest();
        request.setHumanReviewApproved(true);
        request.setPromptText("Use brand tone and campaign objective.");
        request.setOutputText("Draft subject line");
        request.setContext(Map.of("apiToken", "SECRET-VALUE", "campaignType", "newsletter"));

        Map<String, Object> result = service.evaluate(request);

        ArgumentCaptor<Map<String, Object>> values = ArgumentCaptor.forClass(Map.class);
        verify(repository).insert(eq("ai_content_assistance_audits"), values.capture(), anyList());
        Map<String, Object> saved = values.getValue();

        assertThat(result.get("decision")).isEqualTo("APPROVED_DRAFT_ONLY");
        assertThat(saved.get("policy_version")).isEqualTo("v3");
        assertThat(saved.get("created_by")).isEqualTo("user-1");
        assertThat(saved.get("prompt_template_version")).isEqualTo("subject-v1");
        assertThat((String) saved.get("prompt_hash")).hasSize(64);
        assertThat((String) saved.get("output_hash")).hasSize(64);
        assertThat(saved.get("raw_prompt_stored")).isEqualTo(false);
        assertThat(saved.get("raw_output_stored")).isEqualTo(false);
        assertThat(saved.toString()).doesNotContain("Use brand tone", "Draft subject line", "SECRET-VALUE");
        assertThat(saved.get("request_context").toString()).contains("REDACTED");
    }

    private AiContentAssistancePolicyRequest policyRequest() {
        AiContentAssistancePolicyRequest request = new AiContentAssistancePolicyRequest();
        request.setPolicyKey("draft-content");
        request.setName("Draft content assistance");
        request.setProviderName("INTERNAL_RULES");
        request.setModelName("none");
        request.setAllowedDataClasses(List.of("BRAND_KIT", "TEMPLATE_FRAGMENT", "CAMPAIGN_OBJECTIVE"));
        request.setProhibitedDataClasses(List.of("SUBSCRIBER_EVENT_HISTORY", "RAW_SUBSCRIBER_ATTRIBUTES"));
        request.setRequireHumanReview(true);
        request.setDraftOnly(true);
        request.setVersionLabel("v3");
        return request;
    }

    private AiContentAssistanceEvaluateRequest evaluateRequest() {
        AiContentAssistanceEvaluateRequest request = new AiContentAssistanceEvaluateRequest();
        request.setPolicyKey("draft-content");
        request.setRequestedAction("draft_suggestion");
        request.setArtifactType("EMAIL_TEMPLATE");
        request.setArtifactId("template-1");
        request.setPromptTemplateVersion("subject-v1");
        request.setRequestedDataClasses(List.of("BRAND_KIT", "CAMPAIGN_OBJECTIVE"));
        request.setEvidenceRefs(List.of("policy://draft-content/v3"));
        return request;
    }

    private Map<String, Object> policyRow(String status, boolean killSwitch) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "policy-1");
        row.put("workspace_id", "workspace-1");
        row.put("policy_key", "draft-content");
        row.put("status", status);
        row.put("provider_disclosure", Map.of("providerName", "INTERNAL_RULES", "modelName", "none"));
        row.put("allowed_data_classes", List.of("BRAND_KIT", "TEMPLATE_FRAGMENT", "CAMPAIGN_OBJECTIVE"));
        row.put("prohibited_data_classes", List.of("SUBSCRIBER_EVENT_HISTORY", "RAW_SUBSCRIBER_ATTRIBUTES"));
        row.put("require_human_review", true);
        row.put("draft_only", true);
        row.put("kill_switch_enabled", killSwitch);
        row.put("version_label", "v3");
        return row;
    }
}

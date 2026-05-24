package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.AiContentAssistanceEvaluateRequest;
import com.legent.foundation.dto.performance.AiGenerationPreviewRequest;
import com.legent.foundation.dto.performance.AiProviderMeteringRequest;
import com.legent.foundation.service.performance.AiContentAssistanceGovernanceService;
import com.legent.foundation.service.performance.AiGenerationPreviewService;
import com.legent.foundation.service.performance.AiProviderContractMeteringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGenerationPreviewServiceTest {

    @Mock
    private AiContentAssistanceGovernanceService governanceService;

    @Mock
    private AiProviderContractMeteringService meteringService;

    private AiGenerationPreviewService service;

    @BeforeEach
    void setUp() {
        service = new AiGenerationPreviewService(governanceService, meteringService, new ObjectMapper());
    }

    @Test
    void previewCreatesProviderFreeDraftsWhenControlsDoNotDeny() {
        when(governanceService.evaluate(any())).thenReturn(map(
                "id", "audit-1",
                "decision", "REVIEW_REQUIRED",
                "blockedReasons", List.of(),
                "dataClassesBlocked", List.of(),
                "guardrailFindings", List.of()));
        when(meteringService.evaluateMetering(any())).thenReturn(map(
                "id", "meter-1",
                "decision", "APPROVED_METERED",
                "blockedReasons", List.of(),
                "dataClassesBlocked", List.of(),
                "guardrailFindings", List.of()));

        AiGenerationPreviewRequest request = request();
        request.setSegmentHints(map("conditions", List.of(map(
                "field", "source",
                "op", "EQUALS",
                "value", "webinar",
                "valueType", "STRING"))));
        request.setWorkflowHints(map("delayMinutes", 120));
        request.setCampaignId("campaign-1");

        Map<String, Object> response = service.preview(request);

        assertThat(response.get("decision")).isEqualTo("PREVIEW_AVAILABLE");
        assertThat(response.get("previewOnly")).isEqualTo(true);
        assertThat(response.get("draftOnly")).isEqualTo(true);
        assertThat(response.get("applyAllowed")).isEqualTo(false);
        assertThat(response.get("activationAllowed")).isEqualTo(false);
        assertThat(response.get("publishAllowed")).isEqualTo(false);
        assertThat(response.get("providerInvoked")).isEqualTo(false);
        assertThat(response.get("modelInvocation")).isEqualTo("NOT_PERFORMED");
        assertThat(response.get("modelInvocationPerformed")).isEqualTo(false);
        assertThat(response.get("modelBacked")).isEqualTo(false);
        assertThat(response.get("previewHash").toString()).hasSize(64);

        Map<String, Object> segmentDraft = castMap(response.get("segmentDraft"));
        assertThat(segmentDraft.get("status")).isEqualTo("PREVIEW_ONLY");
        assertThat(segmentDraft.get("derivationMode")).isEqualTo("RULE_DERIVED_FALLBACK");
        Map<String, Object> rules = castMap(segmentDraft.get("rules"));
        assertThat(rules.get("conditions").toString()).contains("source", "EQUALS", "webinar");
        Map<String, Object> executionPlan = castMap(segmentDraft.get("executionPlan"));
        assertThat(executionPlan.get("executionMode")).isEqualTo("BOUNDED_SQL");
        assertThat(executionPlan.get("conditionCount")).isEqualTo(1);

        Map<String, Object> workflowDraft = castMap(response.get("workflowDraft"));
        assertThat(workflowDraft.get("publishAllowed")).isEqualTo(false);
        assertThat(workflowDraft.get("runtimeSupported")).isEqualTo(true);
        Map<String, Object> graph = castMap(workflowDraft.get("graph"));
        Map<String, Object> nodes = castMap(graph.get("nodes"));
        assertThat(nodes.keySet()).containsExactly("entry", "delay_1", "send_email_1", "end");
        assertThat(castMap(castMap(nodes.get("send_email_1")).get("configuration")).get("campaignId")).isEqualTo("campaign-1");

        ArgumentCaptor<AiContentAssistanceEvaluateRequest> governanceCaptor = ArgumentCaptor.forClass(AiContentAssistanceEvaluateRequest.class);
        ArgumentCaptor<AiProviderMeteringRequest> meteringCaptor = ArgumentCaptor.forClass(AiProviderMeteringRequest.class);
        verify(governanceService).evaluate(governanceCaptor.capture());
        verify(meteringService).evaluateMetering(meteringCaptor.capture());
        assertThat(governanceCaptor.getValue().getRequestedAction()).isEqualTo("DRAFT_SUGGESTION");
        assertThat(governanceCaptor.getValue().getPromptText()).isEqualTo("Find engaged webinar contacts");
        assertThat(governanceCaptor.getValue().getOutputText()).contains("SEGMENT_DRAFT", "WORKFLOW_DRAFT");
        assertThat(meteringCaptor.getValue().getRequestedAction()).isEqualTo("GENERATE_SEGMENT_WORKFLOW_PREVIEW");
        assertThat(meteringCaptor.getValue().getUnitsRequested()).isEqualByComparingTo("1");
        assertThat(meteringCaptor.getValue().getCostEstimate()).isEqualByComparingTo("0");
    }

    @Test
    void previewHidesDraftsWhenGovernanceDenies() {
        when(governanceService.evaluate(any())).thenReturn(map(
                "id", "audit-1",
                "decision", "DENIED",
                "blockedReasons", List.of("Requested data classes are not allowed by policy."),
                "dataClassesBlocked", List.of("RAW_SUBSCRIBER_ATTRIBUTES"),
                "guardrailFindings", List.of(map("key", "data_classes.blocked", "severity", "BLOCK"))));
        when(meteringService.evaluateMetering(any())).thenReturn(map(
                "id", "meter-1",
                "decision", "APPROVED_METERED",
                "blockedReasons", List.of(),
                "dataClassesBlocked", List.of(),
                "guardrailFindings", List.of()));

        Map<String, Object> response = service.preview(request());

        assertThat(response.get("decision")).isEqualTo("DENIED");
        assertThat(response.get("segmentDraft")).isNull();
        assertThat(response.get("workflowDraft")).isNull();
        assertThat(response.get("providerInvoked")).isEqualTo(false);
        assertThat(response.get("blockedReasons").toString()).contains("Requested data classes");
        assertThat(response.get("dataClassesBlocked").toString()).contains("RAW_SUBSCRIBER_ATTRIBUTES");
    }

    @Test
    void previewRejectsUnsupportedSegmentOperatorBeforeGovernanceSideEffects() {
        AiGenerationPreviewRequest request = request();
        request.setSegmentHints(map("conditions", List.of(map(
                "field", "status",
                "op", "DROP_TABLE",
                "value", "ACTIVE"))));

        assertThatThrownBy(() -> service.preview(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported segment operator");

        verify(governanceService, never()).evaluate(any());
        verify(meteringService, never()).evaluateMetering(any());
    }

    @Test
    void previewRejectsSensitiveContextBeforeGovernanceSideEffects() {
        AiGenerationPreviewRequest request = request();
        request.setContext(map("apiToken", "SECRET-VALUE"));

        assertThatThrownBy(() -> service.preview(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");

        verify(governanceService, never()).evaluate(any());
        verify(meteringService, never()).evaluateMetering(any());
    }

    @Test
    void previewRejectsNestedSensitiveContextBeforeGovernanceSideEffects() {
        AiGenerationPreviewRequest request = request();
        request.setContext(map("provider", map("privateKey", "SECRET-VALUE")));

        assertThatThrownBy(() -> service.preview(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");

        verify(governanceService, never()).evaluate(any());
        verify(meteringService, never()).evaluateMetering(any());
    }

    @Test
    void previewOmitsSendEmailNodeUntilCampaignIdIsProvided() {
        when(governanceService.evaluate(any())).thenReturn(map(
                "id", "audit-1",
                "decision", "REVIEW_REQUIRED",
                "blockedReasons", List.of(),
                "dataClassesBlocked", List.of(),
                "guardrailFindings", List.of()));
        when(meteringService.evaluateMetering(any())).thenReturn(map(
                "id", "meter-1",
                "decision", "APPROVED_METERED",
                "blockedReasons", List.of(),
                "dataClassesBlocked", List.of(),
                "guardrailFindings", List.of()));

        AiGenerationPreviewRequest request = request();
        request.setGenerationTargets(List.of("workflow"));

        Map<String, Object> response = service.preview(request);

        assertThat(response.get("decision")).isEqualTo("PREVIEW_AVAILABLE");
        assertThat(response.get("warnings").toString()).contains("WORKFLOW_PREVIEW_OMITS_SEND_EMAIL");
        Map<String, Object> graph = castMap(castMap(response.get("workflowDraft")).get("graph"));
        Map<String, Object> nodes = castMap(graph.get("nodes"));
        assertThat(nodes.keySet()).containsExactly("entry", "delay_1", "end");
    }

    @Test
    void previewForTargetsLimitsResponseToRequestedArtifactFamily() {
        when(governanceService.evaluate(any())).thenReturn(map(
                "id", "audit-1",
                "decision", "REVIEW_REQUIRED",
                "blockedReasons", List.of(),
                "dataClassesBlocked", List.of(),
                "guardrailFindings", List.of()));
        when(meteringService.evaluateMetering(any())).thenReturn(map(
                "id", "meter-1",
                "decision", "APPROVED_METERED",
                "blockedReasons", List.of(),
                "dataClassesBlocked", List.of(),
                "guardrailFindings", List.of()));

        Map<String, Object> response = service.previewForTargets(request(), List.of("SEGMENT"));

        assertThat(response.get("segmentDraft")).isNotNull();
        assertThat(response.get("workflowDraft")).isNull();
        assertThat(response.get("generationTargets").toString()).contains("SEGMENT").doesNotContain("WORKFLOW");

        ArgumentCaptor<AiProviderMeteringRequest> meteringCaptor = ArgumentCaptor.forClass(AiProviderMeteringRequest.class);
        verify(meteringService).evaluateMetering(meteringCaptor.capture());
        assertThat(meteringCaptor.getValue().getRequestedAction()).isEqualTo("GENERATE_SEGMENT_PREVIEW");
    }

    @Test
    void previewAllowsSafeCustomFieldsButRejectsRelationshipTraversal() {
        when(governanceService.evaluate(any())).thenReturn(map(
                "id", "audit-1",
                "decision", "REVIEW_REQUIRED",
                "blockedReasons", List.of(),
                "dataClassesBlocked", List.of(),
                "guardrailFindings", List.of()));
        when(meteringService.evaluateMetering(any())).thenReturn(map(
                "id", "meter-1",
                "decision", "APPROVED_METERED",
                "blockedReasons", List.of(),
                "dataClassesBlocked", List.of(),
                "guardrailFindings", List.of()));

        AiGenerationPreviewRequest request = request();
        request.setSegmentHints(map("conditions", List.of(map(
                "field", "loyalty_tier",
                "op", "EQUALS",
                "value", "gold"))));

        Map<String, Object> response = service.previewForTargets(request, List.of("SEGMENT"));

        assertThat(response.get("warnings").toString()).contains("CUSTOM_FIELD_FILTER_REQUIRES_INDEX_REVIEW");

        AiGenerationPreviewRequest relationshipRequest = request();
        relationshipRequest.setSegmentHints(map("conditions", List.of(map(
                "field", "status",
                "op", "EQUALS",
                "value", "ACTIVE",
                "relationshipPath", "orders.customer_id"))));

        assertThatThrownBy(() -> service.previewForTargets(relationshipRequest, List.of("SEGMENT")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data extension relationships");
    }

    private AiGenerationPreviewRequest request() {
        AiGenerationPreviewRequest request = new AiGenerationPreviewRequest();
        request.setWorkspaceId("workspace-1");
        request.setPolicyKey("draft-content");
        request.setContractKey("internal-generation");
        request.setProviderKey("internal-rules");
        request.setRequestId("request-1");
        request.setObjective("Find engaged webinar contacts");
        request.setGenerationTargets(List.of("segment", "workflow"));
        request.setRequestedDataClasses(List.of("CAMPAIGN_OBJECTIVE", "SEGMENT_RULES"));
        request.setDisclosureAccepted(true);
        request.setHumanReviewApproved(false);
        request.setContext(map("channel", "email"));
        request.setEvidenceRefs(List.of("policy://draft-content/v1"));
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return values;
    }
}

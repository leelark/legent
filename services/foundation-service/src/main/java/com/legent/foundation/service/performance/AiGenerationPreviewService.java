package com.legent.foundation.service.performance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.AiContentAssistanceEvaluateRequest;
import com.legent.foundation.dto.performance.AiGenerationPreviewRequest;
import com.legent.foundation.dto.performance.AiProviderMeteringRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiGenerationPreviewService {

    private static final List<String> DEFAULT_TARGETS = List.of("SEGMENT", "WORKFLOW");
    private static final Set<String> SUPPORTED_TARGETS = Set.of("SEGMENT", "WORKFLOW");
    private static final Set<String> SUPPORTED_SEGMENT_OPERATORS = Set.of(
            "EQUALS",
            "NOT_EQUALS",
            "CONTAINS",
            "STARTS_WITH",
            "ENDS_WITH",
            "GREATER_THAN",
            "LESS_THAN",
            "IS_NULL",
            "IS_NOT_NULL",
            "IN_LIST",
            "NOT_IN_LIST",
            "IN_SEGMENT");
    private static final Set<String> NULL_OPERATORS = Set.of("IS_NULL", "IS_NOT_NULL");
    private static final Set<String> LIST_OPERATORS = Set.of("IN_LIST", "NOT_IN_LIST");
    private static final Set<String> SUPPORTED_SEGMENT_FIELDS = Set.of(
            "email",
            "first_name",
            "firstname",
            "last_name",
            "lastname",
            "status",
            "source",
            "locale",
            "timezone",
            "phone",
            "subscriber_key",
            "subscriberkey",
            "created_at",
            "createdat",
            "subscribed_at",
            "subscribedat",
            "last_activity_at",
            "lastactivityat",
            "list_membership",
            "segment_membership");
    private static final Set<String> RESERVED_SEGMENT_FIELDS = Set.of(
            "select",
            "insert",
            "update",
            "delete",
            "drop",
            "create",
            "alter",
            "where",
            "and",
            "or",
            "not",
            "null",
            "true",
            "false",
            "union",
            "join",
            "from",
            "table",
            "column",
            "database",
            "exec",
            "execute",
            "script",
            "eval",
            "cast",
            "convert");
    private static final Set<String> UNSUPPORTED_DATA_EXTENSION_FIELDS = Set.of(
            "data_extension",
            "data_extension_field",
            "relationship_path");
    private static final Set<String> SENSITIVE_CONTEXT_KEY_TOKENS = Set.of(
            "secret",
            "token",
            "password",
            "credential",
            "privatekey",
            "private_key",
            "apikey",
            "api_key");
    private static final Set<String> RUNTIME_SUPPORTED_WORKFLOW_NODE_TYPES = Set.of(
            "ENTRY_TRIGGER",
            "SEND_EMAIL",
            "DELAY",
            "CONDITION",
            "END");

    private final AiContentAssistanceGovernanceService aiContentAssistanceGovernanceService;
    private final AiProviderContractMeteringService aiProviderContractMeteringService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> preview(AiGenerationPreviewRequest request) {
        return previewForTargets(request, null);
    }

    public Map<String, Object> previewForTargets(AiGenerationPreviewRequest request, List<String> forcedTargets) {
        String requestId = requireText(request.getRequestId(), "requestId");
        String objective = requireText(request.getObjective(), "objective");
        validateSafeContext(request.getContext(), "context");
        List<String> targets = forcedTargets == null
                ? generationTargets(request.getGenerationTargets())
                : generationTargets(forcedTargets);
        List<String> requestedDataClasses = normalizeList(request.getRequestedDataClasses());
        List<String> warnings = new ArrayList<>();

        Map<String, Object> drafts = buildDrafts(request, targets, objective, warnings);
        String outputText = toJson(drafts);
        String outputHash = sha256(outputText);

        Map<String, Object> governance = aiContentAssistanceGovernanceService.evaluate(
                governanceRequest(request, requestId, objective, requestedDataClasses, outputText, targets));
        Map<String, Object> metering = aiProviderContractMeteringService.evaluateMetering(
                meteringRequest(request, requestId, requestedDataClasses, targets));

        List<String> blockedReasons = new ArrayList<>();
        blockedReasons.addAll(stringList(governance.get("blockedReasons")));
        blockedReasons.addAll(stringList(metering.get("blockedReasons")));
        boolean denied = "DENIED".equals(normalize(governance.get("decision")))
                || "DENIED".equals(normalize(metering.get("decision")));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requestId", requestId);
        response.put("decision", denied ? "DENIED" : "PREVIEW_AVAILABLE");
        response.put("governanceDecision", governance.get("decision"));
        response.put("meteringDecision", metering.get("decision"));
        response.put("governanceAuditId", governance.get("id"));
        response.put("meteringEventId", metering.get("id"));
        response.put("generationTargets", targets);
        response.put("previewOnly", true);
        response.put("draftOnly", true);
        response.put("applyAllowed", false);
        response.put("activationAllowed", false);
        response.put("publishAllowed", false);
        response.put("approvalRequired", true);
        response.put("humanReviewRequired", true);
        response.put("providerInvoked", false);
        response.put("modelInvocation", "NOT_PERFORMED");
        response.put("modelInvocationPerformed", false);
        response.put("modelBacked", false);
        response.put("providerFree", true);
        response.put("dataClassesUsed", requestedDataClasses);
        response.put("dataClassesBlocked", distinctConcat(governance.get("dataClassesBlocked"), metering.get("dataClassesBlocked")));
        response.put("blockedReasons", blockedReasons);
        response.put("guardrailFindings", concatFindings(governance.get("guardrailFindings"), metering.get("guardrailFindings")));
        response.put("warnings", List.copyOf(new LinkedHashSet<>(warnings)));
        response.put("previewHash", outputHash);
        response.put("constraints", constraints());
        response.put("segmentDraft", denied ? null : drafts.get("segmentDraft"));
        response.put("workflowDraft", denied ? null : drafts.get("workflowDraft"));
        response.put("evaluatedAt", Instant.now().toString());
        return response;
    }

    private AiContentAssistanceEvaluateRequest governanceRequest(AiGenerationPreviewRequest request,
                                                                 String requestId,
                                                                 String objective,
                                                                 List<String> requestedDataClasses,
                                                                 String outputText,
                                                                 List<String> targets) {
        AiContentAssistanceEvaluateRequest governanceRequest = new AiContentAssistanceEvaluateRequest();
        governanceRequest.setWorkspaceId(request.getWorkspaceId());
        governanceRequest.setPolicyKey(requireText(request.getPolicyKey(), "policyKey"));
        governanceRequest.setArtifactType("AI_GENERATION_PREVIEW");
        governanceRequest.setArtifactId(requestId);
        governanceRequest.setRequestedAction("DRAFT_SUGGESTION");
        governanceRequest.setPromptTemplateVersion("ai-generation-preview-v1");
        governanceRequest.setPromptText(objective);
        governanceRequest.setOutputText(outputText);
        governanceRequest.setRequestedDataClasses(requestedDataClasses);
        governanceRequest.setHumanReviewApproved(Boolean.TRUE.equals(request.getHumanReviewApproved()));
        governanceRequest.setReviewDecision(Map.of(
                "mode", "PREVIEW_ONLY",
                "applyAllowed", false,
                "providerFree", true));
        governanceRequest.setEvidenceRefs(evidenceRefs(request));
        governanceRequest.setContext(context(request, Map.of(
                "requestId", requestId,
                "generationTargets", targets,
                "objectiveLength", objective.length())));
        return governanceRequest;
    }

    private AiProviderMeteringRequest meteringRequest(AiGenerationPreviewRequest request,
                                                      String requestId,
                                                      List<String> requestedDataClasses,
                                                      List<String> targets) {
        AiProviderMeteringRequest meteringRequest = new AiProviderMeteringRequest();
        meteringRequest.setWorkspaceId(request.getWorkspaceId());
        meteringRequest.setContractKey(requireText(request.getContractKey(), "contractKey"));
        meteringRequest.setProviderKey(blankToNull(request.getProviderKey()));
        meteringRequest.setFeatureKey("AI_SEGMENT_WORKFLOW_GENERATION_PREVIEW");
        meteringRequest.setArtifactType("AI_GENERATION_PREVIEW");
        meteringRequest.setArtifactId(requestId);
        meteringRequest.setRequestId(requestId);
        meteringRequest.setRequestedAction(previewAction(targets));
        meteringRequest.setUnitsRequested(BigDecimal.ONE);
        meteringRequest.setCostEstimate(BigDecimal.ZERO);
        meteringRequest.setCurrencyCode("USD");
        meteringRequest.setRequestedDataClasses(requestedDataClasses);
        meteringRequest.setDisclosureAccepted(Boolean.TRUE.equals(request.getDisclosureAccepted()));
        meteringRequest.setEvidenceRefs(evidenceRefs(request));
        meteringRequest.setContext(context(request, Map.of(
                "requestId", requestId,
                "generationTargets", targets,
                "providerFree", true,
                "modelInvocation", "NOT_PERFORMED")));
        return meteringRequest;
    }

    private Map<String, Object> buildDrafts(AiGenerationPreviewRequest request,
                                            List<String> targets,
                                            String objective,
                                            List<String> warnings) {
        Map<String, Object> drafts = new LinkedHashMap<>();
        if (targets.contains("SEGMENT")) {
            drafts.put("segmentDraft", buildSegmentDraft(request, objective, warnings));
        }
        if (targets.contains("WORKFLOW")) {
            drafts.put("workflowDraft", buildWorkflowDraft(request, objective, targets.contains("SEGMENT"), warnings));
        }
        return drafts;
    }

    private Map<String, Object> buildSegmentDraft(AiGenerationPreviewRequest request,
                                                  String objective,
                                                  List<String> warnings) {
        Map<String, Object> hints = safeMap(request.getSegmentHints());
        List<Map<String, Object>> conditions = segmentConditions(hints, warnings);
        if (conditions.isEmpty()) {
            conditions.add(map("field", "status", "op", "EQUALS", "value", "ACTIVE", "valueType", "STRING"));
            warnings.add("SEGMENT_DEFAULTS_TO_ACTIVE_STATUS_FILTER");
        }

        Map<String, Object> rules = map(
                "operator", "AND",
                "conditions", conditions,
                "groups", List.of());
        Map<String, Object> executionPlan = segmentExecutionPlan(conditions, warnings);

        return map(
                "kind", "SEGMENT_DRAFT",
                "name", firstText(request.getSegmentName(), textHint(hints, "name"), previewName("Segment", objective)),
                "description", "Preview-only segment draft. Human review and audience-service creation are required before use.",
                "segmentType", "DYNAMIC",
                "derivationMode", "RULE_DERIVED_FALLBACK",
                "status", "PREVIEW_ONLY",
                "previewOnly", true,
                "providerFree", true,
                "modelInvocation", "NOT_PERFORMED",
                "draftOnly", true,
                "applyAllowed", false,
                "activationAllowed", false,
                "publishAllowed", false,
                "rules", rules,
                "executionPlan", executionPlan,
                "executionContract", map(
                        "executionMode", "BOUNDED_SQL",
                        "bounded", true,
                        "tenantWorkspaceScoped", true,
                        "relationshipTraversalSupported", false,
                        "supportedOperators", List.copyOf(SUPPORTED_SEGMENT_OPERATORS),
                        "knownSubscriberFields", List.copyOf(SUPPORTED_SEGMENT_FIELDS)));
    }

    private List<Map<String, Object>> segmentConditions(Map<String, Object> hints, List<String> warnings) {
        Object rawConditions = hints.get("conditions");
        if (rawConditions == null) {
            return new ArrayList<>();
        }
        if (!(rawConditions instanceof Collection<?> collection)) {
            throw new IllegalArgumentException("segmentHints.conditions must be an array.");
        }
        List<Map<String, Object>> conditions = new ArrayList<>();
        int index = 0;
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> rawCondition)) {
                throw new IllegalArgumentException("segmentHints.conditions[" + index + "] must be an object.");
            }
            if (conditions.size() >= 5) {
                warnings.add("SEGMENT_CONDITION_LIMIT_APPLIED");
                break;
            }
            Map<String, Object> condition = toStringKeyMap(rawCondition);
            String field = requireText(text(condition.get("field")), "segmentHints.conditions[" + index + "].field");
            String operator = requireText(text(condition.get("op")), "segmentHints.conditions[" + index + "].op").toUpperCase(Locale.ROOT);
            validateNoRelationshipHints(condition, index);
            validateSegmentField(field, index);
            validateSegmentOperator(field, operator, index);

            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("field", normalizeSegmentField(field));
            normalized.put("op", operator);
            if (!NULL_OPERATORS.contains(operator)) {
                Object value = condition.get("value");
                if (value == null || String.valueOf(value).isBlank()) {
                    throw new IllegalArgumentException("segmentHints.conditions[" + index + "].value is required.");
                }
                normalized.put("value", value);
            }
            normalized.put("valueType", normalizeValueType(text(condition.getOrDefault("valueType", "STRING"))));
            conditions.add(normalized);
            index++;
        }
        return conditions;
    }

    private Map<String, Object> segmentExecutionPlan(List<Map<String, Object>> conditions, List<String> warnings) {
        LinkedHashSet<String> requiredIndexes = new LinkedHashSet<>();
        requiredIndexes.add("idx_subscribers_candidate_keyset");
        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < conditions.size(); i++) {
            Map<String, Object> condition = conditions.get(i);
            String field = text(condition.get("field"));
            String operator = normalize(condition.get("op"));
            String family = segmentFieldFamily(field);
            boolean indexedLookup = false;
            String strategy = "SUBSCRIBER_FILTER";
            if ("LIST_MEMBERSHIP".equals(family)) {
                requiredIndexes.add("idx_list_memberships_candidate_active");
                indexedLookup = true;
                strategy = "SCOPED_EXISTS";
            } else if ("SEGMENT_MEMBERSHIP".equals(family)) {
                requiredIndexes.add("idx_segment_memberships_candidate");
                indexedLookup = true;
                strategy = "SCOPED_EXISTS";
            } else if ("CUSTOM_FIELD".equals(family)) {
                requiredIndexes.add("idx_sub_custom_fields");
                warnings.add("CUSTOM_FIELD_FILTER_REQUIRES_INDEX_REVIEW");
            }
            if ("CONTAINS".equals(operator) || "ENDS_WITH".equals(operator)) {
                warnings.add("WILDCARD_TEXT_FILTER_REQUIRES_CARDINALITY_REVIEW");
            }
            steps.add(map(
                    "path", "rules.conditions[" + i + "]",
                    "family", family,
                    "field", field,
                    "operator", operator,
                    "strategy", strategy,
                    "tenantWorkspaceScoped", true,
                    "indexedLookup", indexedLookup));
        }
        return map(
                "executionMode", "BOUNDED_SQL",
                "bounded", true,
                "conditionCount", conditions.size(),
                "maxDepth", 0,
                "requiredIndexes", List.copyOf(requiredIndexes),
                "warnings", List.copyOf(new LinkedHashSet<>(warnings)),
                "steps", steps);
    }

    private Map<String, Object> buildWorkflowDraft(AiGenerationPreviewRequest request,
                                                   String objective,
                                                   boolean segmentDraftPresent,
                                                   List<String> warnings) {
        Map<String, Object> hints = safeMap(request.getWorkflowHints());
        int delayMinutes = intHint(hints, "delayMinutes", 60, 1, 10080);
        int frequencyCapPerDay = intHint(hints, "frequencyCapPerDay", 1, 1, 100);
        int cooldownMinutes = intHint(hints, "cooldownMinutes", 1440, 1, 525600);
        String campaignId = firstText(request.getCampaignId(), textHint(hints, "campaignId"));

        Map<String, Object> entryNode = map(
                "id", "entry",
                "type", "ENTRY_TRIGGER",
                "configuration", map(
                        "triggerType", segmentDraftPresent ? "SEGMENT_PREVIEW_ENTRY" : "MANUAL_REVIEW_ENTRY",
                        "source", "AI_GENERATION_PREVIEW"),
                "nextNodeId", "delay_1",
                "branches", List.of());
        Map<String, Object> delayNode = map(
                "id", "delay_1",
                "type", "DELAY",
                "configuration", map("minutes", delayMinutes),
                "nextNodeId", blankToNull(campaignId) == null ? "end" : "send_email_1",
                "branches", List.of());
        Map<String, Object> endNode = map(
                "id", "end",
                "type", "END",
                "configuration", Map.of(),
                "branches", List.of());

        Map<String, Object> nodes = new LinkedHashMap<>();
        nodes.put("entry", entryNode);
        nodes.put("delay_1", delayNode);
        List<Map<String, Object>> edges = new ArrayList<>();
        edges.add(edge("entry", "delay_1"));

        if (blankToNull(campaignId) == null) {
            warnings.add("WORKFLOW_PREVIEW_OMITS_SEND_EMAIL_UNTIL_CAMPAIGN_ID_IS_PROVIDED");
            edges.add(edge("delay_1", "end"));
        } else {
            Map<String, Object> sendNode = map(
                    "id", "send_email_1",
                    "type", "SEND_EMAIL",
                    "configuration", map("campaignId", campaignId),
                    "nextNodeId", "end",
                    "branches", List.of());
            nodes.put("send_email_1", sendNode);
            edges.add(edge("delay_1", "send_email_1"));
            edges.add(edge("send_email_1", "end"));
        }
        nodes.put("end", endNode);

        return map(
                "kind", "WORKFLOW_DRAFT",
                "name", firstText(request.getWorkflowName(), textHint(hints, "name"), previewName("Workflow", objective)),
                "description", "Preview-only workflow draft. Human review and automation-service validation are required before publish.",
                "status", "PREVIEW_ONLY",
                "previewOnly", true,
                "providerFree", true,
                "modelInvocation", "NOT_PERFORMED",
                "draftOnly", true,
                "applyAllowed", false,
                "activationAllowed", false,
                "publishAllowed", false,
                "runtimeSupported", true,
                "runtimeErrors", List.of(),
                "graph", map(
                        "graphVersion", 2,
                        "initialNodeId", "entry",
                        "nodes", nodes,
                        "edges", edges,
                        "entryPolicy", map(
                                "requireConsent", true,
                                "checkSuppression", true,
                                "frequencyCapPerDay", frequencyCapPerDay),
                        "reentryPolicy", map(
                                "allowReentry", false,
                                "cooldownMinutes", cooldownMinutes)),
                "runtimeContract", map(
                        "runtimeSupportedOnly", true,
                        "normalized", true,
                        "nodeCount", nodes.size(),
                        "initialNodeId", "entry",
                        "supportedNodeTypes", List.copyOf(RUNTIME_SUPPORTED_WORKFLOW_NODE_TYPES),
                        "sendEmailOverridesAllowed", false));
    }

    private Map<String, Object> edge(String source, String target) {
        return map(
                "sourceNodeId", source,
                "targetNodeId", target,
                "edgeType", "DEFAULT");
    }

    private Map<String, Object> constraints() {
        return map(
                "previewOnly", true,
                "draftOnly", true,
                "applyAllowed", false,
                "activationAllowed", false,
                "publishAllowed", false,
                "providerInvoked", false,
                "modelInvocation", "NOT_PERFORMED",
                "modelInvocationPerformed", false,
                "segmentExecutionMode", "BOUNDED_SQL",
                "workflowRuntimeSupportedNodeTypes", List.copyOf(RUNTIME_SUPPORTED_WORKFLOW_NODE_TYPES),
                "requiresHumanReviewBeforeApply", true,
                "requiresAudienceServiceCreation", true,
                "requiresAutomationServiceValidationBeforePublish", true);
    }

    private List<String> generationTargets(List<String> targets) {
        if (targets == null || targets.isEmpty()) {
            return DEFAULT_TARGETS;
        }
        LinkedHashSet<String> normalizedTargets = new LinkedHashSet<>();
        for (String target : targets) {
            String normalized = normalize(target);
            if (!SUPPORTED_TARGETS.contains(normalized)) {
                throw new IllegalArgumentException("Unsupported generation target: " + target);
            }
            normalizedTargets.add(normalized);
        }
        return List.copyOf(normalizedTargets);
    }

    private String previewAction(List<String> targets) {
        if (targets.size() == 1 && targets.contains("SEGMENT")) {
            return "GENERATE_SEGMENT_PREVIEW";
        }
        if (targets.size() == 1 && targets.contains("WORKFLOW")) {
            return "GENERATE_WORKFLOW_PREVIEW";
        }
        return "GENERATE_SEGMENT_WORKFLOW_PREVIEW";
    }

    private String segmentFieldFamily(String field) {
        if ("list_membership".equals(field)) {
            return "LIST_MEMBERSHIP";
        }
        if ("segment_membership".equals(field)) {
            return "SEGMENT_MEMBERSHIP";
        }
        if (SUPPORTED_SEGMENT_FIELDS.contains(field)) {
            return "SUBSCRIBER_FIELD";
        }
        return "CUSTOM_FIELD";
    }

    private void validateNoRelationshipHints(Map<String, Object> condition, int index) {
        for (String key : List.of("dataExtensionId", "relationship", "relationshipName", "relationshipPath")) {
            if (condition.containsKey(key)) {
                throw new IllegalArgumentException("segmentHints.conditions[" + index + "] uses data extension relationships, which are not supported by preview rules yet.");
            }
        }
    }

    private void validateSegmentField(String field, int index) {
        if (!field.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("segmentHints.conditions[" + index + "].field contains invalid characters.");
        }
        if (field.length() > 64) {
            throw new IllegalArgumentException("segmentHints.conditions[" + index + "].field is too long.");
        }
        String normalized = field.toLowerCase(Locale.ROOT);
        if (RESERVED_SEGMENT_FIELDS.contains(normalized)) {
            throw new IllegalArgumentException("segmentHints.conditions[" + index + "].field is reserved.");
        }
        if (UNSUPPORTED_DATA_EXTENSION_FIELDS.contains(normalized)
                || normalized.contains("relationship")) {
            throw new IllegalArgumentException("segmentHints.conditions[" + index + "].field uses data extension relationships, which are not supported by preview rules yet.");
        }
    }

    private void validateSegmentOperator(String field, String operator, int index) {
        if (!SUPPORTED_SEGMENT_OPERATORS.contains(operator)) {
            throw new IllegalArgumentException("Unsupported segment operator in preview: " + operator);
        }
        String normalizedField = normalizeSegmentField(field);
        if (LIST_OPERATORS.contains(operator) && !"list_membership".equals(normalizedField)) {
            throw new IllegalArgumentException("segmentHints.conditions[" + index + "] list operators require list_membership field.");
        }
        if ("list_membership".equals(normalizedField) && !LIST_OPERATORS.contains(operator)) {
            throw new IllegalArgumentException("segmentHints.conditions[" + index + "] list_membership only supports IN_LIST or NOT_IN_LIST.");
        }
        if ("IN_SEGMENT".equals(operator) && !"segment_membership".equals(normalizedField)) {
            throw new IllegalArgumentException("segmentHints.conditions[" + index + "] IN_SEGMENT requires segment_membership field.");
        }
    }

    private String normalizeSegmentField(String field) {
        String normalized = field.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "firstname" -> "first_name";
            case "lastname" -> "last_name";
            case "subscriberkey" -> "subscriber_key";
            case "createdat" -> "created_at";
            case "subscribedat" -> "subscribed_at";
            case "lastactivityat" -> "last_activity_at";
            default -> normalized;
        };
    }

    private String normalizeValueType(String valueType) {
        String normalized = normalize(valueType);
        return switch (normalized) {
            case "STRING", "NUMBER", "DATE", "BOOLEAN" -> normalized;
            default -> "STRING";
        };
    }

    private List<String> evidenceRefs(AiGenerationPreviewRequest request) {
        List<String> refs = new ArrayList<>();
        if (request.getEvidenceRefs() != null) {
            refs.addAll(request.getEvidenceRefs().stream().filter(ref -> ref != null && !ref.isBlank()).toList());
        }
        refs.add("policy://" + requireText(request.getPolicyKey(), "policyKey"));
        refs.add("ai-provider-contract://" + requireText(request.getContractKey(), "contractKey"));
        return refs.stream().distinct().toList();
    }

    private Map<String, Object> context(AiGenerationPreviewRequest request, Map<String, Object> generatedContext) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (request.getContext() != null) {
            context.putAll(request.getContext());
        }
        context.putAll(generatedContext);
        context.put("previewOnly", true);
        context.put("applyAllowed", false);
        return context;
    }

    private void validateSafeContext(Map<String, Object> context, String path) {
        if (context == null || context.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String key = entry.getKey();
            if (isSensitiveContextKey(key)) {
                throw new IllegalArgumentException(path + "." + key + " is not allowed in AI generation preview context.");
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                validateSafeContext(toStringKeyMap(map), path + "." + key);
            }
            if (value instanceof Collection<?> collection) {
                int index = 0;
                for (Object item : collection) {
                    if (item instanceof Map<?, ?> itemMap) {
                        validateSafeContext(toStringKeyMap(itemMap), path + "." + key + "[" + index + "]");
                    }
                    index++;
                }
            }
        }
    }

    private boolean isSensitiveContextKey(String key) {
        String normalized = key == null ? "" : key.replace("-", "_").toLowerCase(Locale.ROOT);
        return SENSITIVE_CONTEXT_KEY_TOKENS.stream().anyMatch(normalized::contains);
    }

    private List<String> normalizeList(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::normalize)
                .distinct()
                .toList();
    }

    private List<String> stringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(item -> item != null && !String.valueOf(item).isBlank())
                    .map(String::valueOf)
                    .toList();
        }
        return List.of();
    }

    private List<String> distinctConcat(Object first, Object second) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(stringList(first));
        values.addAll(stringList(second));
        return List.copyOf(values);
    }

    private List<Map<String, Object>> concatFindings(Object first, Object second) {
        List<Map<String, Object>> findings = new ArrayList<>();
        findings.addAll(mapList(first));
        findings.addAll(mapList(second));
        return findings;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                result.add(toStringKeyMap(map));
            }
        }
        return result;
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private String textHint(Map<String, Object> hints, String key) {
        return text(hints.get(key));
    }

    private int intHint(Map<String, Object> hints, String key, int fallback, int min, int max) {
        Object value = hints.get(key);
        int resolved = fallback;
        if (value instanceof Number number) {
            resolved = number.intValue();
        } else if (value != null && !String.valueOf(value).isBlank()) {
            try {
                resolved = Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                resolved = fallback;
            }
        }
        return Math.max(min, Math.min(max, resolved));
    }

    private String previewName(String prefix, String objective) {
        String normalized = objective.replaceAll("[^a-zA-Z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        if (normalized.length() > 48) {
            normalized = normalized.substring(0, 48).trim();
        }
        return prefix + " preview: " + normalized;
    }

    private String requireText(String value, String field) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required for AI generation preview.");
        }
        return normalized;
    }

    private String firstText(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return values;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize AI generation preview.", e);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash AI generation preview output.", e);
        }
    }
}

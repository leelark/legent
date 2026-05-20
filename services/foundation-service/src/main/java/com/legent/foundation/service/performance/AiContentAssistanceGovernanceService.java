package com.legent.foundation.service.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.AiContentAssistanceEvaluateRequest;
import com.legent.foundation.dto.performance.AiContentAssistancePolicyRequest;
import com.legent.foundation.repository.CorePlatformRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiContentAssistanceGovernanceService extends PerformanceLedgerSupport {

    private static final List<String> DRAFT_ONLY_ACTIONS = List.of(
            "DRAFT_SUGGESTION",
            "DRAFT_REVISION",
            "SUBJECT_LINE_DRAFT",
            "BRAND_REWRITE",
            "APPLY_TO_DRAFT"
    );
    private static final List<String> PUBLISH_OR_SEND_ACTIONS = List.of(
            "PUBLISH",
            "AUTO_PUBLISH",
            "AUTOPUBLISH",
            "SEND",
            "AUTO_SEND",
            "TEST_SEND"
    );
    private static final List<String> SENSITIVE_KEY_TOKENS = List.of(
            "secret",
            "token",
            "password",
            "credential",
            "privatekey",
            "private_key",
            "apikey",
            "api_key"
    );

    public AiContentAssistanceGovernanceService(CorePlatformRepository repository, ObjectMapper objectMapper) {
        super(repository, objectMapper);
    }

    @Transactional
    public Map<String, Object> upsertPolicy(AiContentAssistancePolicyRequest request) {
        String workspaceId = requireWorkspace(request.getWorkspaceId());
        boolean requireHumanReview = request.getRequireHumanReview() == null || request.getRequireHumanReview();
        boolean draftOnly = request.getDraftOnly() == null || request.getDraftOnly();
        if (!requireHumanReview) {
            throw new IllegalArgumentException("AI content assistance policies must require human review.");
        }
        if (!draftOnly) {
            throw new IllegalArgumentException("AI content assistance policies must remain draft-only.");
        }
        String promptStoragePolicy = normalize(defaultValue(request.getPromptStoragePolicy(), "HASH_ONLY"));
        String outputStoragePolicy = normalize(defaultValue(request.getOutputStoragePolicy(), "HASH_ONLY"));
        if (!"HASH_ONLY".equals(promptStoragePolicy) || !"HASH_ONLY".equals(outputStoragePolicy)) {
            throw new IllegalArgumentException("AI content assistance stores prompt and output evidence as hashes only in this governance slice.");
        }

        Map<String, Object> providerDisclosure = providerDisclosure(request);
        List<String> allowedDataClasses = normalizeList(request.getAllowedDataClasses());
        List<String> prohibitedDataClasses = normalizeList(request.getProhibitedDataClasses());
        List<String> overlaps = allowedDataClasses.stream().filter(prohibitedDataClasses::contains).toList();
        if (!overlaps.isEmpty()) {
            throw new IllegalArgumentException("Data classes cannot be both allowed and prohibited: " + overlaps);
        }

        Map<String, Object> values = baseValues(workspaceId);
        values.put("policy_key", request.getPolicyKey().trim());
        values.put("name", request.getName().trim());
        values.put("status", normalize(defaultValue(request.getStatus(), "ACTIVE")));
        values.put("feature_class", normalize(defaultValue(request.getFeatureClass(), "DRAFT_CONTENT_ASSISTANCE")));
        values.put("provider_disclosure", toJson(providerDisclosure));
        values.put("allowed_data_classes", toJson(allowedDataClasses));
        values.put("prohibited_data_classes", toJson(prohibitedDataClasses));
        values.put("training_usage_allowed", Boolean.TRUE.equals(request.getTrainingUsageAllowed()));
        values.put("retention_policy", toJson(sanitizeMap(request.getRetentionPolicy())));
        values.put("prompt_storage_policy", promptStoragePolicy);
        values.put("output_storage_policy", outputStoragePolicy);
        values.put("opt_in_required", request.getOptInRequired() == null || request.getOptInRequired());
        values.put("opt_out_enabled", request.getOptOutEnabled() == null || request.getOptOutEnabled());
        values.put("require_human_review", true);
        values.put("draft_only", true);
        values.put("kill_switch_enabled", Boolean.TRUE.equals(request.getKillSwitchEnabled()));
        values.put("version_label", defaultValue(request.getVersionLabel(), "v1"));
        values.put("metadata", toJson(sanitizeMap(request.getMetadata())));
        return upsertByKey("ai_content_assistance_policies", "policy_key", request.getPolicyKey().trim(), workspaceId, values,
                List.of("provider_disclosure", "allowed_data_classes", "prohibited_data_classes", "retention_policy", "metadata"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPolicies(String workspaceId) {
        return listScoped("ai_content_assistance_policies", requireWorkspace(workspaceId), "created_at DESC");
    }

    @Transactional
    public Map<String, Object> evaluate(AiContentAssistanceEvaluateRequest request) {
        String workspaceId = requireWorkspace(request.getWorkspaceId());
        Map<String, Object> policy = findPolicy(workspaceId, request.getPolicyKey());
        String action = normalize(request.getRequestedAction());
        List<String> requestedDataClasses = normalizeList(request.getRequestedDataClasses());
        List<String> allowedDataClasses = readStringList(policy.get("allowed_data_classes")).stream().map(this::normalize).toList();
        List<String> prohibitedDataClasses = readStringList(policy.get("prohibited_data_classes")).stream().map(this::normalize).toList();
        List<String> blockedDataClasses = new ArrayList<>();
        List<String> blockedReasons = new ArrayList<>();
        List<Map<String, Object>> findings = new ArrayList<>();

        evaluatePolicyState(policy, blockedReasons, findings);
        evaluateAction(action, request, blockedReasons, findings);
        evaluateDataClasses(requestedDataClasses, allowedDataClasses, prohibitedDataClasses, blockedDataClasses, blockedReasons, findings);

        boolean humanReviewed = Boolean.TRUE.equals(request.getHumanReviewApproved());
        boolean denied = !blockedReasons.isEmpty();
        boolean reviewRequired = !denied && bool(policy.get("require_human_review")) && !humanReviewed;
        String decision = denied ? "DENIED" : reviewRequired ? "REVIEW_REQUIRED" : "APPROVED_DRAFT_ONLY";
        String promptHash = hashOrNull(firstText(request.getPromptHash(), request.getPromptText()));
        String outputHash = hashOrNull(firstText(request.getOutputHash(), request.getOutputText()));
        Map<String, Object> reviewDecision = sanitizedReviewDecision(request, decision, blockedReasons);

        Map<String, Object> values = baseValues(workspaceId);
        values.put("policy_id", policy.get("id"));
        values.put("policy_key", request.getPolicyKey().trim());
        values.put("policy_version", defaultValue(asString(policy.get("version_label")), "v1"));
        values.put("artifact_type", blankToNull(request.getArtifactType()));
        values.put("artifact_id", blankToNull(request.getArtifactId()));
        values.put("requested_action", action);
        values.put("decision", decision);
        values.put("prompt_template_version", blankToNull(request.getPromptTemplateVersion()));
        values.put("prompt_hash", promptHash);
        values.put("output_hash", outputHash);
        values.put("provider_disclosure", toJson(readMap(policy.get("provider_disclosure"))));
        values.put("data_classes_used", toJson(requestedDataClasses));
        values.put("data_classes_blocked", toJson(blockedDataClasses));
        values.put("guardrail_findings", toJson(findings));
        values.put("review_decision", toJson(reviewDecision));
        values.put("evidence_refs", toJson(request.getEvidenceRefs() == null ? List.of() : request.getEvidenceRefs()));
        values.put("request_context", toJson(sanitizeMap(request.getContext())));
        values.put("human_reviewed", humanReviewed);
        values.put("provider_invoked", false);
        values.put("raw_prompt_stored", false);
        values.put("raw_output_stored", false);
        Map<String, Object> saved = repository.insert("ai_content_assistance_audits", values,
                List.of("provider_disclosure", "data_classes_used", "data_classes_blocked", "guardrail_findings",
                        "review_decision", "evidence_refs", "request_context"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", saved.get("id"));
        response.put("policyId", policy.get("id"));
        response.put("policyKey", request.getPolicyKey());
        response.put("policyVersion", values.get("policy_version"));
        response.put("decision", decision);
        response.put("draftOnly", true);
        response.put("humanReviewRequired", bool(policy.get("require_human_review")));
        response.put("humanReviewed", humanReviewed);
        response.put("providerInvoked", false);
        response.put("modelInvocation", "NOT_PERFORMED");
        response.put("requestedAction", action);
        response.put("blockedReasons", blockedReasons);
        response.put("guardrailFindings", findings);
        response.put("dataClassesUsed", requestedDataClasses);
        response.put("dataClassesBlocked", blockedDataClasses);
        response.put("promptHash", promptHash);
        response.put("outputHash", outputHash);
        response.put("reviewDecision", reviewDecision);
        response.put("evaluatedAt", Instant.now().toString());
        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAudits(String workspaceId, int limit) {
        return listLatest("ai_content_assistance_audits", requireWorkspace(workspaceId), clamp(limit, 1, 500));
    }

    private void evaluatePolicyState(Map<String, Object> policy,
                                     List<String> blockedReasons,
                                     List<Map<String, Object>> findings) {
        if (!"ACTIVE".equals(normalize(asString(policy.get("status"))))) {
            blockedReasons.add("AI content assistance policy is not active.");
            findings.add(finding("policy.status", "BLOCK", "Policy must be ACTIVE before content assistance is allowed."));
        }
        if (bool(policy.get("kill_switch_enabled"))) {
            blockedReasons.add("AI content assistance policy kill switch is enabled.");
            findings.add(finding("policy.kill_switch", "BLOCK", "Kill switch blocks all content assistance."));
        }
        if (!bool(policy.get("draft_only"))) {
            blockedReasons.add("AI content assistance policy is not draft-only.");
            findings.add(finding("policy.draft_only", "BLOCK", "Content assistance must remain draft-only."));
        }
        if (!bool(policy.get("require_human_review"))) {
            blockedReasons.add("AI content assistance policy does not require human review.");
            findings.add(finding("policy.human_review", "BLOCK", "Human review is required before any generated content can progress."));
        }
        Map<String, Object> providerDisclosure = readMap(policy.get("provider_disclosure"));
        if (blankToNull(asString(providerDisclosure.get("providerName"))) == null) {
            blockedReasons.add("Provider disclosure is missing providerName.");
            findings.add(finding("provider.disclosure", "BLOCK", "Provider disclosure must include providerName."));
        }
    }

    private void evaluateAction(String action,
                                AiContentAssistanceEvaluateRequest request,
                                List<String> blockedReasons,
                                List<Map<String, Object>> findings) {
        if (PUBLISH_OR_SEND_ACTIONS.contains(action)) {
            blockedReasons.add("AI content assistance cannot publish, send, auto-publish, or test-send content.");
            findings.add(finding("action.draft_only", "BLOCK", "Only draft suggestion or draft revision actions are allowed."));
        } else if (!DRAFT_ONLY_ACTIONS.contains(action)) {
            blockedReasons.add("Unsupported AI content assistance action: " + action + ".");
            findings.add(finding("action.unsupported", "BLOCK", "Requested action must be a draft-only content assistance action."));
        }
        if ("APPLY_TO_DRAFT".equals(action) && !Boolean.TRUE.equals(request.getHumanReviewApproved())) {
            blockedReasons.add("Applying generated content to a draft requires human review.");
            findings.add(finding("review.required", "BLOCK", "Human review is required before generated output can be applied to a draft."));
        }
    }

    private void evaluateDataClasses(List<String> requestedDataClasses,
                                     List<String> allowedDataClasses,
                                     List<String> prohibitedDataClasses,
                                     List<String> blockedDataClasses,
                                     List<String> blockedReasons,
                                     List<Map<String, Object>> findings) {
        for (String dataClass : requestedDataClasses) {
            boolean prohibited = prohibitedDataClasses.contains(dataClass);
            boolean notAllowed = !allowedDataClasses.isEmpty() && !allowedDataClasses.contains(dataClass);
            if (prohibited || notAllowed) {
                blockedDataClasses.add(dataClass);
            }
        }
        if (!blockedDataClasses.isEmpty()) {
            blockedReasons.add("Requested data classes are not allowed by policy: " + blockedDataClasses + ".");
            findings.add(finding("data_classes.blocked", "BLOCK", "Prompt context contains data classes outside the policy allowlist."));
        }
    }

    private Map<String, Object> providerDisclosure(AiContentAssistancePolicyRequest request) {
        String providerName = blankToNull(request.getProviderName());
        if (providerName == null) {
            throw new IllegalArgumentException("Provider disclosure must include providerName.");
        }
        Map<String, Object> disclosure = new LinkedHashMap<>(sanitizeMap(request.getProviderDisclosure()));
        disclosure.put("providerName", providerName);
        putIfPresent(disclosure, "modelName", request.getModelName());
        putIfPresent(disclosure, "deploymentRegion", request.getDeploymentRegion());
        putIfPresent(disclosure, "processor", request.getProcessor());
        return disclosure;
    }

    private Map<String, Object> sanitizedReviewDecision(AiContentAssistanceEvaluateRequest request, String decision, List<String> blockedReasons) {
        Map<String, Object> reviewDecision = new LinkedHashMap<>(sanitizeMap(request.getReviewDecision()));
        reviewDecision.put("decision", decision);
        reviewDecision.put("reviewedBy", actor());
        reviewDecision.put("blockedReasons", blockedReasons);
        return reviewDecision;
    }

    private Map<String, Object> findPolicy(String workspaceId, String policyKey) {
        List<Map<String, Object>> rows = repository.queryForList("""
                SELECT * FROM ai_content_assistance_policies
                WHERE tenant_id = :tenantId
                  AND workspace_id = :workspaceId
                  AND policy_key = :policyKey
                  AND deleted_at IS NULL
                ORDER BY updated_at DESC
                LIMIT 1
                """, map("tenantId", tenant(), "workspaceId", workspaceId, "policyKey", policyKey));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("AI content assistance policy not found: " + policyKey);
        }
        return rows.get(0);
    }

    private String requireWorkspace(String workspaceId) {
        String resolved = workspace(workspaceId);
        if (resolved == null) {
            throw new IllegalArgumentException("workspaceId is required for AI content assistance governance.");
        }
        return resolved;
    }

    private List<String> normalizeList(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private Map<String, Object> sanitizeMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        value.forEach((key, item) -> sanitized.put(key, sanitizeValue(key, item)));
        return sanitized;
    }

    private Object sanitizeValue(String key, Object value) {
        if (sensitiveKey(key)) {
            return "REDACTED";
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((childKey, childValue) -> sanitized.put(String.valueOf(childKey), sanitizeValue(String.valueOf(childKey), childValue)));
            return sanitized;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(item -> sanitizeValue(key, item)).toList();
        }
        return value;
    }

    private boolean sensitiveKey(String key) {
        String normalized = key == null ? "" : key.replace("-", "_").toLowerCase(Locale.ROOT);
        return SENSITIVE_KEY_TOKENS.stream().anyMatch(normalized::contains);
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        String trimmed = blankToNull(value);
        if (trimmed != null) {
            target.put(key, trimmed);
        }
    }

    private String hashOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.matches("(?i)[a-f0-9]{64}")) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(trimmed.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash AI content assistance payload", e);
        }
    }
}

package com.legent.foundation.service.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.AiProviderContractRequest;
import com.legent.foundation.dto.performance.AiProviderMeteringRequest;
import com.legent.foundation.repository.CorePlatformRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiProviderContractMeteringService extends PerformanceLedgerSupport {

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

    public AiProviderContractMeteringService(CorePlatformRepository repository, ObjectMapper objectMapper) {
        super(repository, objectMapper);
    }

    @Transactional
    public Map<String, Object> upsertContract(AiProviderContractRequest request) {
        String workspaceId = requireWorkspace(request.getWorkspaceId());
        String contractKey = requireText(request.getContractKey(), "contractKey");
        String providerKey = requireText(request.getProviderKey(), "providerKey");
        String providerName = requireText(request.getProviderName(), "providerName");
        String modelName = requireText(request.getModelName(), "modelName");
        String promptStoragePolicy = normalize(defaultValue(request.getPromptStoragePolicy(), "HASH_ONLY"));
        String outputStoragePolicy = normalize(defaultValue(request.getOutputStoragePolicy(), "HASH_ONLY"));
        if (!"HASH_ONLY".equals(promptStoragePolicy) || !"HASH_ONLY".equals(outputStoragePolicy)) {
            throw new IllegalArgumentException("AI provider contracts must store prompt and output evidence as hashes only.");
        }

        List<String> allowedDataClasses = normalizeList(request.getAllowedDataClasses());
        List<String> prohibitedDataClasses = normalizeList(request.getProhibitedDataClasses());
        List<String> overlaps = allowedDataClasses.stream().filter(prohibitedDataClasses::contains).toList();
        if (!overlaps.isEmpty()) {
            throw new IllegalArgumentException("Data classes cannot be both allowed and prohibited: " + overlaps);
        }

        Map<String, Object> values = baseValues(workspaceId);
        values.put("contract_key", contractKey);
        values.put("provider_key", providerKey);
        values.put("provider_name", providerName);
        values.put("model_name", modelName);
        values.put("status", normalize(defaultValue(request.getStatus(), "ACTIVE")));
        values.put("feature_class", normalize(defaultValue(request.getFeatureClass(), "AI_PROVIDER_ACCESS")));
        values.put("provider_disclosure", toJson(providerDisclosure(request, providerKey, providerName, modelName)));
        values.put("allowed_data_classes", toJson(allowedDataClasses));
        values.put("prohibited_data_classes", toJson(prohibitedDataClasses));
        values.put("training_usage_allowed", Boolean.TRUE.equals(request.getTrainingUsageAllowed()));
        values.put("opt_in_required", request.getOptInRequired() == null || request.getOptInRequired());
        values.put("opt_out_enabled", request.getOptOutEnabled() == null || request.getOptOutEnabled());
        values.put("require_human_review", request.getRequireHumanReview() == null || request.getRequireHumanReview());
        values.put("metering_enabled", request.getMeteringEnabled() == null || request.getMeteringEnabled());
        values.put("kill_switch_enabled", Boolean.TRUE.equals(request.getKillSwitchEnabled()));
        values.put("prompt_storage_policy", promptStoragePolicy);
        values.put("output_storage_policy", outputStoragePolicy);
        values.put("max_units_per_request", valueOrZero(request.getMaxUnitsPerRequest()));
        values.put("monthly_unit_limit", valueOrZero(request.getMonthlyUnitLimit()));
        values.put("cost_policy", toJson(sanitizeMap(request.getCostPolicy())));
        values.put("retention_policy", toJson(sanitizeMap(request.getRetentionPolicy())));
        values.put("version_label", defaultValue(request.getVersionLabel(), "v1"));
        values.put("metadata", toJson(sanitizeMap(request.getMetadata())));
        return upsertByKey("ai_provider_contracts", "contract_key", contractKey, workspaceId, values,
                List.of("provider_disclosure", "allowed_data_classes", "prohibited_data_classes",
                        "cost_policy", "retention_policy", "metadata"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listContracts(String workspaceId) {
        return listScoped("ai_provider_contracts", requireWorkspace(workspaceId), "created_at DESC");
    }

    @Transactional
    public Map<String, Object> evaluateMetering(AiProviderMeteringRequest request) {
        String workspaceId = requireWorkspace(request.getWorkspaceId());
        String contractKey = requireText(request.getContractKey(), "contractKey");
        Map<String, Object> contract = findContract(workspaceId, contractKey);
        String requestedAction = normalize(requireText(request.getRequestedAction(), "requestedAction"));
        BigDecimal unitsRequested = valueOrZero(request.getUnitsRequested());
        List<String> requestedDataClasses = normalizeList(request.getRequestedDataClasses());
        List<String> allowedDataClasses = readStringList(contract.get("allowed_data_classes")).stream().map(this::normalize).toList();
        List<String> prohibitedDataClasses = readStringList(contract.get("prohibited_data_classes")).stream().map(this::normalize).toList();
        List<String> blockedReasons = new ArrayList<>();
        List<String> blockedDataClasses = new ArrayList<>();
        List<Map<String, Object>> findings = new ArrayList<>();

        evaluateContractState(contract, blockedReasons, findings);
        evaluateProviderMatch(request, contract, blockedReasons, findings);
        evaluateDisclosureAcceptance(request, blockedReasons, findings);
        evaluateDataClasses(requestedDataClasses, allowedDataClasses, prohibitedDataClasses, blockedDataClasses, blockedReasons, findings);
        evaluateUnitLimit(unitsRequested, contract.get("max_units_per_request"), blockedReasons, findings);

        String decision = blockedReasons.isEmpty() ? "APPROVED_METERED" : "DENIED";
        Map<String, Object> providerDisclosure = readMap(contract.get("provider_disclosure"));
        Map<String, Object> policySnapshot = policySnapshot(contract, providerDisclosure);

        Map<String, Object> values = baseValues(workspaceId);
        values.put("contract_id", contract.get("id"));
        values.put("contract_key", contractKey);
        values.put("provider_key", contract.get("provider_key"));
        values.put("provider_name", contract.get("provider_name"));
        values.put("model_name", contract.get("model_name"));
        values.put("feature_key", blankToNull(request.getFeatureKey()));
        values.put("artifact_type", blankToNull(request.getArtifactType()));
        values.put("artifact_id", blankToNull(request.getArtifactId()));
        values.put("request_id", requireText(request.getRequestId(), "requestId"));
        values.put("requested_action", requestedAction);
        values.put("decision", decision);
        values.put("units_requested", unitsRequested);
        values.put("cost_estimate", valueOrZero(request.getCostEstimate()));
        values.put("currency_code", defaultValue(request.getCurrencyCode(), "USD").trim().toUpperCase(Locale.ROOT));
        values.put("data_classes_requested", toJson(requestedDataClasses));
        values.put("data_classes_blocked", toJson(blockedDataClasses));
        values.put("provider_disclosure", toJson(providerDisclosure));
        values.put("policy_snapshot", toJson(policySnapshot));
        values.put("guardrail_findings", toJson(findings));
        values.put("evidence_refs", toJson(request.getEvidenceRefs() == null ? List.of() : request.getEvidenceRefs()));
        values.put("request_context", toJson(sanitizeMap(request.getContext())));
        values.put("provider_invoked", false);
        values.put("raw_prompt_stored", false);
        values.put("raw_output_stored", false);
        Map<String, Object> saved = repository.insert("ai_provider_metering_events", values,
                List.of("data_classes_requested", "data_classes_blocked", "provider_disclosure", "policy_snapshot",
                        "guardrail_findings", "evidence_refs", "request_context"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", saved.get("id"));
        response.put("contractId", contract.get("id"));
        response.put("contractKey", contractKey);
        response.put("providerKey", contract.get("provider_key"));
        response.put("modelName", contract.get("model_name"));
        response.put("requestedAction", requestedAction);
        response.put("decision", decision);
        response.put("meteringRecorded", true);
        response.put("providerInvoked", false);
        response.put("modelInvocation", "NOT_PERFORMED");
        response.put("unitsRequested", unitsRequested);
        response.put("costEstimate", valueOrZero(request.getCostEstimate()));
        response.put("blockedReasons", blockedReasons);
        response.put("dataClassesBlocked", blockedDataClasses);
        response.put("guardrailFindings", findings);
        response.put("evaluatedAt", Instant.now().toString());
        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMeteringEvents(String workspaceId, int limit) {
        return listLatest("ai_provider_metering_events", requireWorkspace(workspaceId), clamp(limit, 1, 500));
    }

    private void evaluateContractState(Map<String, Object> contract,
                                       List<String> blockedReasons,
                                       List<Map<String, Object>> findings) {
        if (!"ACTIVE".equals(normalize(asString(contract.get("status"))))) {
            blockedReasons.add("AI provider contract is not active.");
            findings.add(finding("contract.status", "BLOCK", "Provider contract must be ACTIVE before model-backed work is allowed."));
        }
        if (bool(contract.get("kill_switch_enabled"))) {
            blockedReasons.add("AI provider contract kill switch is enabled.");
            findings.add(finding("contract.kill_switch", "BLOCK", "Kill switch blocks all provider-backed AI work."));
        }
        if (!bool(contract.get("metering_enabled"))) {
            blockedReasons.add("AI provider contract metering is disabled.");
            findings.add(finding("contract.metering", "BLOCK", "Metering must be enabled before provider-backed AI work is allowed."));
        }
        Map<String, Object> disclosure = readMap(contract.get("provider_disclosure"));
        if (blankToNull(asString(disclosure.get("providerName"))) == null
                || blankToNull(asString(disclosure.get("modelName"))) == null) {
            blockedReasons.add("AI provider disclosure is incomplete.");
            findings.add(finding("provider.disclosure", "BLOCK", "Provider disclosure must include providerName and modelName."));
        }
    }

    private void evaluateProviderMatch(AiProviderMeteringRequest request,
                                       Map<String, Object> contract,
                                       List<String> blockedReasons,
                                       List<Map<String, Object>> findings) {
        String requestedProviderKey = blankToNull(request.getProviderKey());
        if (requestedProviderKey != null && !requestedProviderKey.equals(contract.get("provider_key"))) {
            blockedReasons.add("Requested provider does not match the AI provider contract.");
            findings.add(finding("provider.contract_mismatch", "BLOCK", "Provider key must match the approved contract."));
        }
    }

    private void evaluateDisclosureAcceptance(AiProviderMeteringRequest request,
                                              List<String> blockedReasons,
                                              List<Map<String, Object>> findings) {
        if (!Boolean.TRUE.equals(request.getDisclosureAccepted())) {
            blockedReasons.add("Provider disclosure must be accepted before AI provider use.");
            findings.add(finding("provider.disclosure_acceptance", "BLOCK", "A reviewed disclosure acceptance is required before provider-backed AI work."));
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
            blockedReasons.add("Requested data classes are not allowed by the AI provider contract: " + blockedDataClasses + ".");
            findings.add(finding("data_classes.blocked", "BLOCK", "Provider request contains data classes outside the contract allowlist."));
        }
    }

    private void evaluateUnitLimit(BigDecimal unitsRequested,
                                   Object maxUnitsPerRequest,
                                   List<String> blockedReasons,
                                   List<Map<String, Object>> findings) {
        BigDecimal maxUnits = decimal(maxUnitsPerRequest);
        if (maxUnits.compareTo(BigDecimal.ZERO) > 0 && unitsRequested.compareTo(maxUnits) > 0) {
            blockedReasons.add("Requested AI provider units exceed the contract limit.");
            findings.add(finding("metering.unit_limit", "BLOCK", "Requested units must stay within the approved per-request limit."));
        }
    }

    private Map<String, Object> providerDisclosure(AiProviderContractRequest request,
                                                   String providerKey,
                                                   String providerName,
                                                   String modelName) {
        Map<String, Object> disclosure = new LinkedHashMap<>(sanitizeMap(request.getProviderDisclosure()));
        disclosure.put("providerKey", providerKey);
        disclosure.put("providerName", providerName);
        disclosure.put("modelName", modelName);
        putIfPresent(disclosure, "deploymentRegion", request.getDeploymentRegion());
        putIfPresent(disclosure, "processor", request.getProcessor());
        disclosure.put("providerInvocationAllowed", false);
        return disclosure;
    }

    private Map<String, Object> policySnapshot(Map<String, Object> contract, Map<String, Object> providerDisclosure) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("contractId", contract.get("id"));
        snapshot.put("contractKey", contract.get("contract_key"));
        snapshot.put("providerKey", contract.get("provider_key"));
        snapshot.put("providerName", contract.get("provider_name"));
        snapshot.put("modelName", contract.get("model_name"));
        snapshot.put("status", contract.get("status"));
        snapshot.put("versionLabel", contract.get("version_label"));
        snapshot.put("meteringEnabled", contract.get("metering_enabled"));
        snapshot.put("killSwitchEnabled", contract.get("kill_switch_enabled"));
        snapshot.put("providerDisclosure", providerDisclosure);
        return snapshot;
    }

    private Map<String, Object> findContract(String workspaceId, String contractKey) {
        List<Map<String, Object>> rows = repository.queryForList("""
                SELECT * FROM ai_provider_contracts
                WHERE tenant_id = :tenantId
                  AND workspace_id = :workspaceId
                  AND contract_key = :contractKey
                  AND deleted_at IS NULL
                ORDER BY updated_at DESC
                LIMIT 1
                """, map("tenantId", tenant(), "workspaceId", workspaceId, "contractKey", contractKey));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("AI provider contract not found: " + contractKey);
        }
        return rows.get(0);
    }

    private String requireWorkspace(String workspaceId) {
        String resolved = workspace(workspaceId);
        if (resolved == null) {
            throw new IllegalArgumentException("workspaceId is required for AI provider contracts.");
        }
        return resolved;
    }

    private String requireText(String value, String field) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required for AI provider contracts.");
        }
        return normalized;
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

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value != null) {
            try {
                return new BigDecimal(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

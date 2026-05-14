package com.legent.foundation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.util.IdGenerator;
import com.legent.foundation.dto.DifferentiationDto;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DifferentiationPlatformService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() {};

    private final CorePlatformRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> createCopilotRecommendation(DifferentiationDto.CopilotRecommendationRequest request) {
        String workspaceId = workspace(request.getWorkspaceId());
        Map<String, Object> policy = safeMap(request.getPolicyContext());
        int riskScore = copilotRiskScore(request, policy);
        boolean approvalRequired = Boolean.TRUE.equals(request.getRequireHumanApproval())
                || riskScore >= 50
                || containsIgnoreCase(request.getConstraints(), "human approval");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("riskScore", riskScore);
        payload.put("riskBand", riskBand(riskScore));
        payload.put("approvalRequired", approvalRequired);
        payload.put("recommendations", copilotRecommendations(request, policy, riskScore));
        payload.put("policyFindings", policyFindings(policy, request));

        Map<String, Object> values = baseValues(workspaceId);
        values.put("artifact_type", normalize(request.getArtifactType()));
        values.put("artifact_id", blankToNull(request.getArtifactId()));
        values.put("objective", request.getObjective().trim());
        values.put("audience_summary", blankToNull(request.getAudienceSummary()));
        values.put("risk_score", riskScore);
        values.put("approval_required", approvalRequired);
        values.put("status", approvalRequired ? "PENDING_APPROVAL" : "READY");
        values.put("policy_context", toJson(policy));
        values.put("candidate_content", toJson(request.getCandidateContent()));
        values.put("constraints", toJson(request.getConstraints() == null ? List.of() : request.getConstraints()));
        values.put("recommendations", toJson(payload));
        values.put("approved_by", null);
        values.put("approved_at", null);
        values.put("human_decision_note", null);
        return repository.insert("ai_copilot_recommendations", values, List.of(
                "policy_context", "candidate_content", "constraints", "recommendations"));
    }

    @Transactional
    public Map<String, Object> decideCopilotRecommendation(String id, DifferentiationDto.CopilotDecisionRequest request) {
        Map<String, Object> existing = requireById("ai_copilot_recommendations", id);
        String decision = normalize(request.getDecision());
        if (!List.of("APPROVED", "REJECTED").contains(decision)) {
            throw new IllegalArgumentException("decision must be APPROVED or REJECTED");
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("status", decision);
        updates.put("approved_by", actor());
        updates.put("approved_at", Instant.now());
        updates.put("human_decision_note", blankToNull(request.getDecisionNote()));
        return repository.updateByIdAndWorkspace("ai_copilot_recommendations", id, tenant(), asString(existing.get("workspace_id")), updates, List.of());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCopilotRecommendations(String workspaceId, int limit) {
        Map<String, Object> params = scopedParams(workspaceId);
        params.put("limit", clamp(limit, 1, 200));
        return repository.queryForList("""
                SELECT * FROM ai_copilot_recommendations
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT :limit
                """, params);
    }

    @Transactional
    public Map<String, Object> upsertDecisionPolicy(DifferentiationDto.DecisionPolicyRequest request) {
        String workspaceId = workspace(request.getWorkspaceId());
        Map<String, Object> values = baseValues(workspaceId);
        values.put("policy_key", request.getPolicyKey().trim());
        values.put("name", request.getName().trim());
        values.put("status", defaultValue(request.getStatus(), "ACTIVE"));
        values.put("trigger_event", defaultValue(request.getTriggerEvent(), "PROFILE_UPDATED"));
        values.put("channel", defaultValue(request.getChannel(), "ANY"));
        values.put("rules", toJson(request.getRules()));
        values.put("variants", toJson(request.getVariants() == null ? List.of() : request.getVariants()));
        values.put("guardrails", toJson(request.getGuardrails()));
        values.put("metadata", toJson(request.getMetadata()));
        return upsertByKey("realtime_decision_policies", "policy_key", request.getPolicyKey().trim(), workspaceId, values,
                List.of("rules", "variants", "guardrails", "metadata"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDecisionPolicies(String workspaceId) {
        return listScoped("realtime_decision_policies", workspaceId, "created_at DESC");
    }

    @Transactional
    public Map<String, Object> evaluateDecisionPolicy(DifferentiationDto.DecisionEvaluateRequest request) {
        Map<String, Object> params = scopedParams(null);
        params.put("policyKey", request.getPolicyKey());
        List<Map<String, Object>> rows = repository.queryForList("""
                SELECT * FROM realtime_decision_policies
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND policy_key = :policyKey
                  AND status = 'ACTIVE'
                  AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT 1
                """, params);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Active decision policy not found: " + request.getPolicyKey());
        }
        Map<String, Object> policy = rows.get(0);
        List<Map<String, Object>> variants = readMapList(policy.get("variants"));
        Map<String, Object> guardrails = readMap(policy.get("guardrails"));
        Map<String, Object> profile = safeMap(request.getProfileUpdates());
        Map<String, Object> selected = selectVariant(variants, profile, guardrails, request.getChannel());
        double confidence = decisionConfidence(selected, profile, guardrails);

        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("policyId", policy.get("id"));
        decision.put("policyKey", policy.get("policy_key"));
        decision.put("eventType", defaultValue(request.getEventType(), String.valueOf(policy.get("trigger_event"))));
        decision.put("selectedVariant", selected);
        decision.put("confidence", confidence);
        decision.put("eligible", !selected.isEmpty());
        decision.put("explanation", selected.isEmpty()
                ? "No eligible variant after guardrails."
                : "Variant selected from profile affinity, explicit channel preference, and guardrail fit.");
        decision.put("guardrailResult", guardrailResult(profile, guardrails, request.getChannel()));
        decision.put("decidedAt", Instant.now().toString());

        Map<String, Object> event = baseValues(asString(policy.get("workspace_id")));
        event.put("policy_id", policy.get("id"));
        event.put("subject_id", asString(profile.get("subjectId")));
        event.put("event_type", defaultValue(request.getEventType(), "PROFILE_UPDATED"));
        event.put("input_profile", toJson(profile));
        event.put("decision", toJson(decision));
        event.put("confidence", confidence);
        repository.insert("realtime_decision_events", event, List.of("input_profile", "decision"));
        return decision;
    }

    @Transactional
    public Map<String, Object> upsertOmnichannelFlow(DifferentiationDto.OmnichannelFlowRequest request) {
        String workspaceId = workspace(request.getWorkspaceId());
        Map<String, Object> values = baseValues(workspaceId);
        values.put("flow_key", request.getFlowKey().trim());
        values.put("name", request.getName().trim());
        values.put("status", defaultValue(request.getStatus(), "ACTIVE"));
        values.put("transactional", request.getTransactional() != null && request.getTransactional());
        values.put("channels", toJson(normalizedChannels(request.getChannels())));
        values.put("routing_rules", toJson(request.getRoutingRules()));
        values.put("guardrails", toJson(request.getGuardrails()));
        values.put("metadata", toJson(request.getMetadata()));
        return upsertByKey("omnichannel_orchestration_flows", "flow_key", request.getFlowKey().trim(), workspaceId, values,
                List.of("channels", "routing_rules", "guardrails", "metadata"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOmnichannelFlows(String workspaceId) {
        return listScoped("omnichannel_orchestration_flows", workspaceId, "created_at DESC");
    }

    @Transactional
    public Map<String, Object> simulateOmnichannelFlow(DifferentiationDto.OmnichannelSimulationRequest request) {
        Map<String, Object> params = scopedParams(null);
        params.put("flowKey", request.getFlowKey());
        List<Map<String, Object>> flows = repository.queryForList("""
                SELECT * FROM omnichannel_orchestration_flows
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND flow_key = :flowKey
                  AND status = 'ACTIVE'
                  AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT 1
                """, params);
        if (flows.isEmpty()) {
            throw new IllegalArgumentException("Active omnichannel flow not found: " + request.getFlowKey());
        }
        Map<String, Object> flow = flows.get(0);
        List<String> route = routeChannels(readStringList(flow.get("channels")), request.getPreferredChannels(), safeMap(request.getRecipient()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("flowId", flow.get("id"));
        result.put("flowKey", flow.get("flow_key"));
        result.put("route", route);
        result.put("primaryChannel", route.isEmpty() ? null : route.get(0));
        result.put("transactional", Boolean.TRUE.equals(flow.get("transactional")));
        result.put("blocked", route.isEmpty());
        result.put("simulationNotes", route.isEmpty()
                ? List.of("No channel has consent or eligibility.")
                : List.of("Route respects consent, preferred channel, and flow fallback order."));
        result.put("simulatedAt", Instant.now().toString());

        Map<String, Object> run = baseValues(asString(flow.get("workspace_id")));
        run.put("flow_id", flow.get("id"));
        run.put("recipient", toJson(request.getRecipient()));
        run.put("event_payload", toJson(request.getEvent()));
        run.put("route", toJson(route));
        run.put("result", toJson(result));
        repository.insert("omnichannel_simulation_runs", run, List.of("recipient", "event_payload", "route", "result"));
        return result;
    }

    @Transactional
    public Map<String, Object> upsertDeveloperPackage(DifferentiationDto.DeveloperPackageRequest request) {
        Map<String, Object> values = baseValues(null);
        values.put("app_key", request.getAppKey().trim());
        values.put("display_name", request.getDisplayName().trim());
        values.put("status", defaultValue(request.getStatus(), "ACTIVE"));
        values.put("api_version", defaultValue(request.getApiVersion(), "v1"));
        values.put("scopes", toJson(request.getScopes() == null ? List.of() : request.getScopes()));
        values.put("sdk_targets", toJson(request.getSdkTargets() == null ? List.of("typescript") : request.getSdkTargets()));
        values.put("sandbox_enabled", request.getSandboxEnabled() == null || request.getSandboxEnabled());
        values.put("marketplace_status", defaultValue(request.getMarketplaceStatus(), "PRIVATE"));
        values.put("webhook_replay_enabled", request.getWebhookReplayEnabled() == null || request.getWebhookReplayEnabled());
        values.put("metadata", toJson(request.getMetadata()));
        return upsertByKey("developer_app_packages", "app_key", request.getAppKey().trim(), null, values,
                List.of("scopes", "sdk_targets", "metadata"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDeveloperPackages() {
        return repository.queryForList("""
                SELECT * FROM developer_app_packages
                WHERE tenant_id = :tenantId AND deleted_at IS NULL
                ORDER BY created_at DESC
                """, Map.of("tenantId", tenant()));
    }

    @Transactional
    public Map<String, Object> createSandbox(DifferentiationDto.SandboxRequest request) {
        assertPackage(request.getAppPackageId(), true);
        Map<String, Object> values = baseValues(null);
        values.put("app_package_id", request.getAppPackageId());
        values.put("status", "READY");
        values.put("data_profile", defaultValue(request.getDataProfile(), "SMALL"));
        values.put("seed_options", toJson(request.getSeedOptions()));
        values.put("api_base_url", "sandbox://tenant/" + tenant() + "/apps/" + request.getAppPackageId());
        values.put("expires_at", Instant.now().plusSeconds(14L * 24 * 3600));
        return repository.insert("developer_sandboxes", values, List.of("seed_options"));
    }

    @Transactional
    public Map<String, Object> createWebhookReplay(DifferentiationDto.WebhookReplayRequest request) {
        Map<String, Object> appPackage = assertPackage(request.getAppPackageId(), false);
        if (!Boolean.TRUE.equals(appPackage.get("webhook_replay_enabled"))) {
            throw new IllegalArgumentException("webhook replay is disabled for this app package");
        }
        boolean dryRun = request.getDryRun() == null || request.getDryRun();
        Map<String, Object> values = baseValues(null);
        values.put("app_package_id", request.getAppPackageId());
        values.put("source_webhook_id", blankToNull(request.getSourceWebhookId()));
        values.put("target_url", blankToNull(request.getTargetUrl()));
        values.put("event_types", toJson(request.getEventTypes() == null ? List.of() : request.getEventTypes()));
        values.put("from_time", request.getFromTime());
        values.put("to_time", request.getToTime());
        values.put("dry_run", dryRun);
        values.put("status", dryRun ? "DRY_RUN_COMPLETE" : "QUEUED");
        values.put("estimated_events", Math.max(1, request.getEventTypes() == null ? 1 : request.getEventTypes().size()) * 100L);
        values.put("created_by", actor());
        return repository.insert("webhook_replay_jobs", values, List.of("event_types"));
    }

    @Transactional
    public Map<String, Object> upsertSloPolicy(DifferentiationDto.SloPolicyRequest request) {
        String workspaceId = workspace(request.getWorkspaceId());
        Map<String, Object> values = baseValues(workspaceId);
        values.put("service_name", request.getServiceName().trim());
        values.put("status", defaultValue(request.getStatus(), "ACTIVE"));
        values.put("slo_target_percent", request.getSloTargetPercent() == null ? 99.9 : request.getSloTargetPercent());
        values.put("slo_window", defaultValue(request.getWindow(), "30d"));
        values.put("error_budget_minutes", request.getErrorBudgetMinutes() == null ? 43.2 : request.getErrorBudgetMinutes());
        values.put("synthetic_probe", toJson(request.getSyntheticProbe()));
        values.put("self_healing_actions", toJson(request.getSelfHealingActions() == null ? List.of() : request.getSelfHealingActions()));
        values.put("capacity_forecast", toJson(request.getCapacityForecast()));
        values.put("incident_automation", toJson(request.getIncidentAutomation()));
        return upsertByKey("slo_operations_policies", "service_name", request.getServiceName().trim(), workspaceId, values,
                List.of("synthetic_probe", "self_healing_actions", "capacity_forecast", "incident_automation"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSloPolicies(String workspaceId) {
        return listScoped("slo_operations_policies", workspaceId, "service_name ASC");
    }

    @Transactional
    public Map<String, Object> evaluateSloPolicy(DifferentiationDto.SloEvaluateRequest request) {
        Map<String, Object> params = scopedParams(null);
        params.put("serviceName", request.getServiceName());
        List<Map<String, Object>> policies = repository.queryForList("""
                SELECT * FROM slo_operations_policies
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND service_name = :serviceName
                  AND status = 'ACTIVE'
                  AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT 1
                """, params);
        if (policies.isEmpty()) {
            throw new IllegalArgumentException("Active SLO policy not found: " + request.getServiceName());
        }
        Map<String, Object> policy = policies.get(0);
        double target = number(policy.get("slo_target_percent"), 99.9);
        double success = request.getSuccessRatePercent() == null ? 100.0 : request.getSuccessRatePercent();
        double budgetBurnPercent = budgetBurn(target, success);
        Map<String, Object> probe = readMap(policy.get("synthetic_probe"));
        long p95Threshold = Math.round(number(probe.get("p95LatencyMs"), 1500));
        long p95 = request.getP95LatencyMs() == null ? 0L : request.getP95LatencyMs();
        double saturation = request.getSaturationPercent() == null ? 0.0 : request.getSaturationPercent();
        boolean incident = budgetBurnPercent >= 100 || saturation >= 90 || (p95Threshold > 0 && p95 > p95Threshold);
        boolean selfHealing = incident && !readMapList(policy.get("self_healing_actions")).isEmpty();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policyId", policy.get("id"));
        result.put("serviceName", request.getServiceName());
        result.put("targetPercent", target);
        result.put("successRatePercent", success);
        result.put("errorBudgetBurnPercent", budgetBurnPercent);
        result.put("p95LatencyMs", p95);
        result.put("p95ThresholdMs", p95Threshold);
        result.put("saturationPercent", saturation);
        result.put("incidentStatus", incident ? "ACTIVE" : "OK");
        result.put("selfHealingTriggered", selfHealing);
        result.put("capacityForecast", forecastCapacity(readMap(policy.get("capacity_forecast")), saturation, request.getQueueDepth()));
        result.put("recommendedActions", sloActions(incident, selfHealing, budgetBurnPercent, saturation, p95, p95Threshold));
        result.put("evaluatedAt", Instant.now().toString());

        if (incident) {
            Map<String, Object> event = baseValues(asString(policy.get("workspace_id")));
            event.put("slo_policy_id", policy.get("id"));
            event.put("service_name", request.getServiceName());
            event.put("severity", budgetBurnPercent >= 200 || saturation >= 95 ? "P1" : "P2");
            event.put("status", selfHealing ? "SELF_HEALING_STARTED" : "OPEN");
            event.put("telemetry", toJson(requestTelemetry(request)));
            event.put("automation_result", toJson(result));
            repository.insert("slo_incident_automation_events", event, List.of("telemetry", "automation_result"));
        }
        return result;
    }

    private Map<String, Object> upsertByKey(String table,
                                            String keyColumn,
                                            String keyValue,
                                            String workspaceId,
                                            Map<String, Object> values,
                                            List<String> jsonColumns) {
        String safeTable = CorePlatformRepository.safeTable(table);
        String safeKeyColumn = CorePlatformRepository.safeKeyColumn(keyColumn);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", tenant());
        params.put("workspaceId", workspaceId);
        params.put("keyValue", keyValue);
        List<Map<String, Object>> existing = repository.queryForList(
                "SELECT id FROM " + safeTable + " WHERE tenant_id = :tenantId AND (:workspaceId IS NULL OR workspace_id = :workspaceId) AND " + safeKeyColumn + " = :keyValue AND deleted_at IS NULL LIMIT 1",
                params
        );
        if (existing.isEmpty()) {
            return repository.insert(table, values, jsonColumns);
        }
        Map<String, Object> updates = new LinkedHashMap<>(values);
        updates.remove("id");
        updates.remove("tenant_id");
        updates.remove("workspace_id");
        updates.remove("created_at");
        updates.remove("created_by");
        updates.remove("deleted_at");
        updates.remove("version");
        if (workspaceId != null) {
            return repository.updateByIdAndWorkspace(table, String.valueOf(existing.get(0).get("id")), tenant(), workspaceId, updates, jsonColumns);
        }
        return repository.updateById(table, String.valueOf(existing.get(0).get("id")), tenant(), updates, jsonColumns);
    }

    private Map<String, Object> requireById(String table, String id) {
        String safeTable = CorePlatformRepository.safeTable(table);
        String workspaceId = workspace(null);
        List<Map<String, Object>> rows = repository.queryForList(
                "SELECT * FROM " + safeTable + " WHERE tenant_id = :tenantId AND workspace_id = :workspaceId AND id = :id AND deleted_at IS NULL LIMIT 1",
                map("tenantId", tenant(), "workspaceId", workspaceId, "id", id)
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(safeTable + " not found: " + id);
        }
        return rows.get(0);
    }

    private List<Map<String, Object>> listScoped(String table, String workspaceId, String orderBy) {
        Map<String, Object> params = scopedParams(workspaceId);
        return repository.queryForList(
                "SELECT * FROM " + CorePlatformRepository.safeTable(table) + " WHERE tenant_id = :tenantId AND (:workspaceId IS NULL OR workspace_id = :workspaceId) AND deleted_at IS NULL ORDER BY " + CorePlatformRepository.safeOrderBy(orderBy),
                params
        );
    }

    private Map<String, Object> assertPackage(String appPackageId, boolean requireSandbox) {
        List<Map<String, Object>> packages = repository.queryForList("""
                SELECT * FROM developer_app_packages
                WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL
                LIMIT 1
                """, map("tenantId", tenant(), "id", appPackageId));
        if (packages.isEmpty()) {
            throw new IllegalArgumentException("developer app package not found: " + appPackageId);
        }
        Map<String, Object> appPackage = packages.get(0);
        if (requireSandbox && !Boolean.TRUE.equals(appPackage.get("sandbox_enabled"))) {
            throw new IllegalArgumentException("sandbox is disabled for this app package");
        }
        return appPackage;
    }

    private int copilotRiskScore(DifferentiationDto.CopilotRecommendationRequest request, Map<String, Object> policy) {
        int risk = 0;
        if (Boolean.TRUE.equals(policy.get("commercial"))) {
            risk += 15;
        }
        if (number(policy.get("consentCoveragePercent"), 100) < 95) {
            risk += 30;
        }
        if (Boolean.TRUE.equals(policy.get("usesSensitiveData"))) {
            risk += 25;
        }
        if (number(policy.get("estimatedRecipients"), 0) >= 100_000) {
            risk += 15;
        }
        if (containsIgnoreCase(request.getConstraints(), "regulated")) {
            risk += 15;
        }
        if ("LOW".equalsIgnoreCase(defaultValue(request.getRiskTolerance(), ""))) {
            risk += 10;
        }
        return clamp(risk, 0, 100);
    }

    private List<Map<String, Object>> copilotRecommendations(DifferentiationDto.CopilotRecommendationRequest request,
                                                             Map<String, Object> policy,
                                                             int riskScore) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        recommendations.add(rec("POLICY_REVIEW", "Run consent, suppression, frequency, and brand checks before activation.", riskScore >= 40 ? "HIGH" : "MEDIUM"));
        recommendations.add(rec("JOURNEY_OPTIMIZATION", "Use engaged-first entry, wait node, exit goal, and throttle ramp when risk is above low.", riskScore >= 50 ? "HIGH" : "LOW"));
        recommendations.add(rec("CONTENT_VARIANTS", "Generate subject and CTA variants, keep claims evidence-backed, and require reviewer approval for high-risk segments.", "MEDIUM"));
        if (Boolean.TRUE.equals(policy.get("commercial"))) {
            recommendations.add(rec("COMPLIANCE_FOOTER", "Require unsubscribe, sender identity, and send classification before launch.", "HIGH"));
        }
        if (number(policy.get("estimatedRecipients"), 0) >= 100_000) {
            recommendations.add(rec("DELIVERABILITY_RAMP", "Use warmup-aware split, provider capacity checks, and failover-safe send windows.", "HIGH"));
        }
        if (request.getCandidateContent() != null && !request.getCandidateContent().isEmpty()) {
            recommendations.add(rec("PERSONALIZATION", "Use profile attributes only when consent purpose matches campaign objective.", "MEDIUM"));
        }
        return recommendations;
    }

    private List<String> policyFindings(Map<String, Object> policy, DifferentiationDto.CopilotRecommendationRequest request) {
        List<String> findings = new ArrayList<>();
        if (number(policy.get("consentCoveragePercent"), 100) < 95) {
            findings.add("Consent coverage below 95%; launch needs remediation or tighter audience.");
        }
        if (Boolean.TRUE.equals(policy.get("usesSensitiveData"))) {
            findings.add("Sensitive-data personalization requires explicit legal basis and approval.");
        }
        if (containsIgnoreCase(request.getConstraints(), "brand")) {
            findings.add("Brand constraint present; generated copy should stay in approval workflow.");
        }
        return findings;
    }

    private Map<String, Object> selectVariant(List<Map<String, Object>> variants,
                                              Map<String, Object> profile,
                                              Map<String, Object> guardrails,
                                              String requestedChannel) {
        List<String> blockedChannels = readStringList(guardrails.get("blockedChannels"));
        return variants.stream()
                .filter(variant -> channelAllowed(asString(variant.get("channel")), requestedChannel, blockedChannels))
                .max(Comparator.comparingDouble(variant -> variantScore(variant, profile, requestedChannel)))
                .orElse(Collections.emptyMap());
    }

    private boolean channelAllowed(String variantChannel, String requestedChannel, List<String> blockedChannels) {
        String channel = defaultValue(variantChannel, "ANY").toUpperCase(Locale.ROOT);
        if (!requestedChannelEmpty(requestedChannel) && !"ANY".equals(channel) && !channel.equals(normalize(requestedChannel))) {
            return false;
        }
        return blockedChannels.stream().noneMatch(blocked -> normalize(blocked).equals(channel));
    }

    private double variantScore(Map<String, Object> variant, Map<String, Object> profile, String requestedChannel) {
        double score = number(variant.get("weight"), 1);
        Object affinity = profile.get("interest");
        Object tag = variant.get("tag");
        if (affinity != null && tag != null && String.valueOf(affinity).equalsIgnoreCase(String.valueOf(tag))) {
            score += 25;
        }
        if (!requestedChannelEmpty(requestedChannel) && normalize(requestedChannel).equals(normalize(asString(variant.get("channel"))))) {
            score += 10;
        }
        score += number(variant.get("scoreBoost"), 0);
        return score;
    }

    private double decisionConfidence(Map<String, Object> selected, Map<String, Object> profile, Map<String, Object> guardrails) {
        if (selected.isEmpty()) {
            return 0.0;
        }
        double confidence = 0.55 + Math.min(0.25, number(selected.get("weight"), 0) / 400.0);
        if (profile.containsKey("interest")) {
            confidence += 0.10;
        }
        if (!guardrails.isEmpty()) {
            confidence += 0.05;
        }
        return round2(Math.min(0.95, confidence));
    }

    private Map<String, Object> guardrailResult(Map<String, Object> profile, Map<String, Object> guardrails, String requestedChannel) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestedChannel", requestedChannel);
        result.put("blockedChannels", readStringList(guardrails.get("blockedChannels")));
        result.put("profileConsent", profile.getOrDefault("consent", "UNKNOWN"));
        result.put("passed", !"OPT_OUT".equalsIgnoreCase(String.valueOf(profile.getOrDefault("consent", ""))));
        return result;
    }

    private List<String> routeChannels(List<String> configuredChannels, List<String> preferredChannels, Map<String, Object> recipient) {
        List<String> flowChannels = configuredChannels.isEmpty()
                ? List.of("EMAIL", "SMS", "PUSH", "ADS", "WEB", "TRANSACTIONAL")
                : configuredChannels;
        List<String> ordered = new ArrayList<>();
        if (preferredChannels != null) {
            preferredChannels.stream().map(this::normalize).filter(flowChannels::contains).forEach(ordered::add);
        }
        flowChannels.stream().map(this::normalize).filter(channel -> !ordered.contains(channel)).forEach(ordered::add);
        return ordered.stream()
                .filter(channel -> hasChannelConsent(channel, recipient))
                .toList();
    }

    private boolean hasChannelConsent(String channel, Map<String, Object> recipient) {
        Object consent = recipient.get("consent");
        if (consent instanceof Map<?, ?> consentMap) {
            Object value = consentMap.get(channel.toLowerCase(Locale.ROOT));
            if (value == null) {
                value = consentMap.get(channel);
            }
            return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
        }
        if ("TRANSACTIONAL".equals(channel)) {
            return true;
        }
        return !"OPT_OUT".equalsIgnoreCase(String.valueOf(recipient.getOrDefault("consent", "")));
    }

    private Map<String, Object> forecastCapacity(Map<String, Object> forecast, double saturation, Long queueDepth) {
        Map<String, Object> result = new LinkedHashMap<>(forecast);
        long depth = queueDepth == null ? 0L : queueDepth;
        result.put("projectedSaturationPercent", round2(Math.max(saturation, saturation + Math.min(30, depth / 1000.0))));
        result.put("needsCapacity", saturation >= 80 || depth >= 10_000);
        return result;
    }

    private List<String> sloActions(boolean incident, boolean selfHealing, double budgetBurn, double saturation, long p95, long p95Threshold) {
        if (!incident) {
            return List.of("Continue normal monitoring.");
        }
        List<String> actions = new ArrayList<>();
        if (budgetBurn >= 100) {
            actions.add("Open incident and freeze risky deploys until error budget recovers.");
        }
        if (saturation >= 90) {
            actions.add("Scale workers or reduce tenant send concurrency.");
        }
        if (p95Threshold > 0 && p95 > p95Threshold) {
            actions.add("Run latency playbook and inspect downstream dependency saturation.");
        }
        if (selfHealing) {
            actions.add("Execute configured self-healing action with audit evidence.");
        }
        return actions;
    }

    private double budgetBurn(double target, double success) {
        double allowedFailure = Math.max(0.001, 100.0 - target);
        double actualFailure = Math.max(0.0, 100.0 - success);
        return round2((actualFailure / allowedFailure) * 100.0);
    }

    private Map<String, Object> requestTelemetry(DifferentiationDto.SloEvaluateRequest request) {
        Map<String, Object> telemetry = new LinkedHashMap<>();
        telemetry.put("successRatePercent", request.getSuccessRatePercent());
        telemetry.put("p95LatencyMs", request.getP95LatencyMs());
        telemetry.put("saturationPercent", request.getSaturationPercent());
        telemetry.put("queueDepth", request.getQueueDepth());
        telemetry.put("requests", request.getRequests());
        telemetry.put("errors", request.getErrors());
        return telemetry;
    }

    private List<String> normalizedChannels(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return List.of("EMAIL", "SMS", "PUSH", "ADS", "WEB", "TRANSACTIONAL");
        }
        return channels.stream().map(this::normalize).distinct().toList();
    }

    private Map<String, Object> baseValues(String workspaceId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", IdGenerator.newId());
        values.put("tenant_id", tenant());
        values.put("workspace_id", workspaceId);
        values.put("created_at", Instant.now());
        values.put("updated_at", Instant.now());
        values.put("created_by", actor());
        values.put("deleted_at", null);
        values.put("version", 0L);
        return values;
    }

    private Map<String, Object> scopedParams(String workspaceId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", tenant());
        params.put("workspaceId", workspace(workspaceId));
        return params;
    }

    private Map<String, Object> rec(String key, String detail, String priority) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("detail", detail);
        row.put("priority", priority);
        return row;
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Collections.emptyMap() : value;
    }

    private Map<String, Object> readMap(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, val) -> result.put(String.valueOf(key), val));
            return result;
        }
        try {
            return objectMapper.readValue(String.valueOf(value), MAP_TYPE);
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private List<Map<String, Object>> readMapList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                result.add(readMap(item));
            }
            return result;
        }
        try {
            return objectMapper.readValue(String.valueOf(value), MAP_LIST_TYPE);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> readStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(item -> normalize(String.valueOf(item))).toList();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), new TypeReference<List<String>>() {})
                    .stream().map(this::normalize).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        Object safe = value == null ? Collections.emptyMap() : value;
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize differentiation payload", e);
        }
    }

    private String tenant() {
        return TenantContext.requireTenantId();
    }

    private String workspace(String explicit) {
        String resolved = blankToNull(explicit);
        String contextWorkspaceId = blankToNull(TenantContext.getWorkspaceId());
        if (resolved != null && contextWorkspaceId != null && !contextWorkspaceId.equals(resolved)) {
            throw new IllegalArgumentException("workspaceId does not match the current workspace");
        }
        return resolved == null ? contextWorkspaceId : resolved;
    }

    private String actor() {
        String actor = TenantContext.getUserId();
        return actor == null || actor.isBlank() ? "SYSTEM" : actor;
    }

    private boolean requestedChannelEmpty(String requestedChannel) {
        return requestedChannel == null || requestedChannel.isBlank() || "ANY".equalsIgnoreCase(requestedChannel);
    }

    private boolean containsIgnoreCase(List<String> values, String needle) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        String normalizedNeedle = needle.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value != null)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(normalizedNeedle));
    }

    private String riskBand(int riskScore) {
        if (riskScore >= 70) {
            return "HIGH";
        }
        if (riskScore >= 40) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private double number(Object value, double fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return result;
    }
}

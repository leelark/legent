package com.legent.foundation.service.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.performance.OptimizationEvaluateRequest;
import com.legent.foundation.dto.performance.OptimizationPolicyRequest;
import com.legent.foundation.repository.CorePlatformRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClosedLoopOptimizationService extends PerformanceLedgerSupport {

    private static final List<String> OPTIMIZATION_TYPES = List.of("DELIVERABILITY", "ENGAGEMENT", "REVENUE", "CONSENT");

    public ClosedLoopOptimizationService(CorePlatformRepository repository, ObjectMapper objectMapper) {
        super(repository, objectMapper);
    }

    @Transactional
    public Map<String, Object> upsertPolicy(OptimizationPolicyRequest request) {
        String optimizationType = normalize(request.getOptimizationType());
        if (!OPTIMIZATION_TYPES.contains(optimizationType)) {
            throw new IllegalArgumentException("optimizationType must be one of " + OPTIMIZATION_TYPES);
        }
        String workspaceId = workspace(request.getWorkspaceId());
        Map<String, Object> values = baseValues(workspaceId);
        values.put("policy_key", request.getPolicyKey().trim());
        values.put("name", request.getName().trim());
        values.put("optimization_type", optimizationType);
        values.put("status", normalize(defaultValue(request.getStatus(), "ACTIVE")));
        values.put("objective", blankToNull(request.getObjective()));
        values.put("target_metric", blankToNull(request.getTargetMetric()));
        values.put("guardrails", toJson(request.getGuardrails()));
        values.put("rollback_policy", toJson(request.getRollbackPolicy()));
        values.put("approval_policy", toJson(request.getApprovalPolicy()));
        values.put("metadata", toJson(request.getMetadata()));
        return upsertByKey("performance_optimization_policies", "policy_key", request.getPolicyKey().trim(), workspaceId, values,
                List.of("guardrails", "rollback_policy", "approval_policy", "metadata"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPolicies(String workspaceId) {
        return listScoped("performance_optimization_policies", workspace(workspaceId), "created_at DESC");
    }

    @Transactional
    public Map<String, Object> evaluate(OptimizationEvaluateRequest request) {
        Map<String, Object> policy = findPolicy(workspace(request.getWorkspaceId()), request.getPolicyKey());
        Map<String, Object> signals = safeMap(request.getSignals());
        Map<String, Object> guardrails = readMap(policy.get("guardrails"));
        Map<String, Object> approvalPolicy = readMap(policy.get("approval_policy"));
        Map<String, Object> rollbackPolicy = readMap(policy.get("rollback_policy"));
        String optimizationType = normalize(asString(policy.get("optimization_type")));

        List<String> blockedReasons = new ArrayList<>();
        List<Map<String, Object>> recommendations = new ArrayList<>();
        int risk = consentRisk(signals, guardrails, blockedReasons, recommendations);
        risk += switch (optimizationType) {
            case "DELIVERABILITY" -> deliverabilityRisk(signals, blockedReasons, recommendations);
            case "ENGAGEMENT" -> engagementRisk(signals, recommendations);
            case "REVENUE" -> revenueRisk(signals, recommendations);
            case "CONSENT" -> consentLoopRisk(signals, recommendations);
            default -> 10;
        };
        risk = clamp(risk, 0, 100);
        int score = blockedReasons.isEmpty() ? 100 - risk : Math.min(30, 100 - risk);
        boolean approvalRequired = !blockedReasons.isEmpty()
                || risk >= 50
                || bool(approvalPolicy.get("requireHumanApproval"))
                || ("REVENUE".equals(optimizationType) && (bool(signals.get("changesAudience")) || number(signals.get("revenueAtRisk"), 0) >= 10000));
        boolean rollbackRequired = bool(rollbackPolicy.get("snapshotRequired"))
                || bool(signals.get("changesAudience"))
                || bool(signals.get("changesContent"))
                || bool(signals.get("autoApplyRequested"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policyId", policy.get("id"));
        result.put("policyKey", request.getPolicyKey());
        result.put("optimizationType", optimizationType);
        result.put("score", score);
        result.put("riskBand", riskBand(risk));
        result.put("approvalRequired", approvalRequired);
        result.put("rollbackRequired", rollbackRequired);
        result.put("blockedReasons", blockedReasons);
        result.put("recommendations", recommendations);
        result.put("evaluatedAt", Instant.now().toString());

        Map<String, Object> values = baseValues(asString(policy.get("workspace_id")));
        values.put("policy_id", policy.get("id"));
        values.put("optimization_type", optimizationType);
        values.put("artifact_type", blankToNull(request.getArtifactType()));
        values.put("artifact_id", blankToNull(request.getArtifactId()));
        values.put("signals", toJson(signals));
        values.put("score", score);
        values.put("risk_band", result.get("riskBand"));
        values.put("recommendations", toJson(recommendations));
        values.put("approval_required", approvalRequired);
        values.put("rollback_required", rollbackRequired);
        values.put("blocked_reasons", toJson(blockedReasons));
        Map<String, Object> saved = repository.insert("performance_optimization_runs", values,
                List.of("signals", "recommendations", "blocked_reasons"));
        result.put("id", saved.get("id"));
        return result;
    }

    private int consentRisk(Map<String, Object> signals,
                            Map<String, Object> guardrails,
                            List<String> blockedReasons,
                            List<Map<String, Object>> recommendations) {
        int risk = 0;
        double minConsent = number(guardrails.get("minConsentCoveragePercent"), 95);
        double consentCoverage = number(signals.get("consentCoveragePercent"), 100);
        if (consentCoverage < minConsent) {
            risk += 45;
            blockedReasons.add("Consent coverage below " + minConsent + "%.");
            recommendations.add(rec("consent.remediate", "BLOCKER", "Reduce audience or collect valid consent before optimization."));
        }
        if (bool(signals.get("missingLegalBasis")) || bool(signals.get("complianceViolation")) || bool(signals.get("consentRevoked"))) {
            risk += 50;
            blockedReasons.add("Consent or compliance guardrail failed.");
            recommendations.add(rec("compliance.block", "BLOCKER", "Hold optimization until legal basis and consent ledger pass."));
        }
        double maxOptOutRate = number(guardrails.get("maxOptOutRatePercent"), 2.0);
        if (number(signals.get("optOutRatePercent"), 0) > maxOptOutRate) {
            risk += 20;
            recommendations.add(rec("consent.optout", "HIGH", "Review targeting and preference-center friction."));
        }
        return risk;
    }

    private int deliverabilityRisk(Map<String, Object> signals, List<String> blockedReasons, List<Map<String, Object>> recommendations) {
        int risk = 0;
        if (number(signals.get("reputationScore"), 100) < 70) {
            risk += 25;
            recommendations.add(rec("deliverability.reputation", "HIGH", "Throttle sends and prioritize engaged recipients."));
        }
        if (number(signals.get("complaintRate"), 0) >= 0.003 || number(signals.get("complaintRatePercent"), 0) >= 0.3) {
            risk += 25;
            recommendations.add(rec("deliverability.complaints", "HIGH", "Suppress complaint sources and pause risky cohorts."));
        }
        if (number(signals.get("hardBounceRate"), 0) >= 0.05 || number(signals.get("hardBounceRatePercent"), 0) >= 5) {
            risk += 20;
            recommendations.add(rec("deliverability.bounces", "HIGH", "Clean invalid addresses before next send."));
        }
        if (number(signals.get("blocklistHits"), 0) > 0) {
            risk += 35;
            blockedReasons.add("Active blocklist hit detected.");
            recommendations.add(rec("deliverability.blocklist", "BLOCKER", "Run remediation playbook before optimization."));
        }
        return risk;
    }

    private int engagementRisk(Map<String, Object> signals, List<Map<String, Object>> recommendations) {
        int risk = 0;
        if (number(signals.get("engagementRate"), 1) < 0.1) {
            risk += 20;
            recommendations.add(rec("engagement.low", "MEDIUM", "Shift to engaged-first audience and test content variants."));
        }
        if (number(signals.get("fatigueRate"), 0) > 0.25) {
            risk += 30;
            recommendations.add(rec("engagement.fatigue", "HIGH", "Apply frequency cap and suppress over-contacted subscribers."));
        }
        return risk;
    }

    private int revenueRisk(Map<String, Object> signals, List<Map<String, Object>> recommendations) {
        int risk = 0;
        if (number(signals.get("revenueAtRisk"), 0) >= 10000) {
            risk += 30;
            recommendations.add(rec("revenue.approval", "HIGH", "Require human approval for high-value revenue optimization."));
        }
        if (number(signals.get("conversionRateDelta"), 0) < 0) {
            risk += 15;
            recommendations.add(rec("revenue.regression", "MEDIUM", "Use rollback snapshot and hold auto-winner until confidence recovers."));
        }
        if (bool(signals.get("changesAudience"))) {
            risk += 20;
            recommendations.add(rec("revenue.audience", "HIGH", "Audience-changing optimization needs consent and rollback review."));
        }
        return risk;
    }

    private int consentLoopRisk(Map<String, Object> signals, List<Map<String, Object>> recommendations) {
        int risk = 0;
        if (number(signals.get("preferenceCenterCompletionRate"), 1) < 0.7) {
            risk += 20;
            recommendations.add(rec("consent.preference_center", "MEDIUM", "Improve preference-center completion before expanding campaigns."));
        }
        if (number(signals.get("suppressionSyncLagMinutes"), 0) > 15) {
            risk += 35;
            recommendations.add(rec("consent.sync_lag", "HIGH", "Fix suppression sync lag before closed-loop decisions."));
        }
        return risk;
    }

    private Map<String, Object> findPolicy(String workspaceId, String policyKey) {
        List<Map<String, Object>> rows = repository.queryForList("""
                SELECT * FROM performance_optimization_policies
                WHERE tenant_id = :tenantId
                  AND COALESCE(workspace_id, '') = COALESCE(:workspaceId, '')
                  AND policy_key = :policyKey
                  AND status = 'ACTIVE'
                  AND deleted_at IS NULL
                ORDER BY updated_at DESC
                LIMIT 1
                """, map("tenantId", tenant(), "workspaceId", workspaceId, "policyKey", policyKey));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Active optimization policy not found: " + policyKey);
        }
        return rows.get(0);
    }
}

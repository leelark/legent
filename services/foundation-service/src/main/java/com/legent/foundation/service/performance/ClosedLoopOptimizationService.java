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

    private static final List<String> OPTIMIZATION_TYPES = List.of("DELIVERABILITY", "ENGAGEMENT", "REVENUE", "CONSENT", "SEND_TIME", "FREQUENCY");

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
        Map<String, Object> sendTimeAssessment = new LinkedHashMap<>();
        Map<String, Object> frequencyAssessment = new LinkedHashMap<>();
        int risk = consentRisk(signals, guardrails, blockedReasons, recommendations);
        risk += switch (optimizationType) {
            case "DELIVERABILITY" -> deliverabilityRisk(signals, blockedReasons, recommendations);
            case "ENGAGEMENT" -> engagementRisk(signals, recommendations);
            case "REVENUE" -> revenueRisk(signals, recommendations);
            case "CONSENT" -> consentLoopRisk(signals, recommendations);
            case "SEND_TIME" -> sendTimeRisk(signals, guardrails, blockedReasons, recommendations, sendTimeAssessment);
            case "FREQUENCY" -> frequencyRisk(signals, guardrails, blockedReasons, recommendations, frequencyAssessment);
            default -> 10;
        };
        risk = clamp(risk, 0, 100);
        int score = blockedReasons.isEmpty() ? 100 - risk : Math.min(30, 100 - risk);
        boolean approvalRequired = !blockedReasons.isEmpty()
                || risk >= 50
                || bool(approvalPolicy.get("requireHumanApproval"))
                || ("SEND_TIME".equals(optimizationType) && bool(signals.get("changesLaunchTiming")))
                || ("FREQUENCY".equals(optimizationType) && bool(frequencyAssessment.get("approvalRequired")))
                || ("REVENUE".equals(optimizationType) && (bool(signals.get("changesAudience")) || number(signals.get("revenueAtRisk"), 0) >= 10000));
        boolean rollbackRequired = bool(rollbackPolicy.get("snapshotRequired"))
                || bool(signals.get("changesAudience"))
                || bool(signals.get("changesContent"))
                || ("SEND_TIME".equals(optimizationType) && bool(signals.get("changesLaunchTiming")))
                || ("FREQUENCY".equals(optimizationType) && bool(frequencyAssessment.get("rollbackRequired")))
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
        if ("SEND_TIME".equals(optimizationType)) {
            result.put("confidenceBand", sendTimeAssessment.get("confidenceBand"));
            result.put("fallbackMode", sendTimeAssessment.get("fallbackMode"));
            result.put("dataQualityReasons", sendTimeAssessment.get("dataQualityReasons"));
            result.put("lookbackDays", sendTimeAssessment.get("lookbackDays"));
        }
        if ("FREQUENCY".equals(optimizationType)) {
            result.put("confidenceBand", frequencyAssessment.get("confidenceBand"));
            result.put("fallbackMode", frequencyAssessment.get("fallbackMode"));
            result.put("dataQualityReasons", frequencyAssessment.get("dataQualityReasons"));
            result.put("lookbackDays", frequencyAssessment.get("lookbackDays"));
            result.put("minimumVariantsMet", frequencyAssessment.get("minimumVariantsMet"));
            result.put("saturationCategory", frequencyAssessment.get("saturationCategory"));
            result.put("recommendedCap", frequencyAssessment.get("recommendedCap"));
            result.put("safetyImpact", frequencyAssessment.get("safetyImpact"));
        }
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

    private int sendTimeRisk(Map<String, Object> signals,
                             Map<String, Object> guardrails,
                             List<String> blockedReasons,
                             List<Map<String, Object>> recommendations,
                             Map<String, Object> assessment) {
        int risk = 0;
        List<String> dataQualityReasons = new ArrayList<>();
        double minEvents = number(guardrails.get("minEligibleEngagementEvents"), 1_000);
        double minContacts = number(guardrails.get("minEligibleContacts"), 500);
        int lookbackDays = (int) number(guardrails.get("lookbackDays"), 90);
        double events = number(signals.get("eligibleEngagementEvents"), 0);
        double contacts = number(signals.get("eligibleContacts"), 0);
        double coveragePercent = number(signals.get("engagementWindowCoveragePercent"), 0);

        if (lookbackDays < 28) {
            risk += 10;
            dataQualityReasons.add("Lookback window is shorter than 28 days.");
        }
        boolean lowData = events < minEvents || contacts < minContacts;
        if (lowData) {
            risk += 25;
            dataQualityReasons.add("Eligible engagement data is below the configured STO threshold.");
            recommendations.add(rec("send_time.fallback", "MEDIUM", "Use the default schedule or tenant-level fallback until enough engagement data exists."));
        }
        if (coveragePercent > 0 && coveragePercent < number(guardrails.get("minEngagementWindowCoveragePercent"), 60)) {
            risk += 15;
            dataQualityReasons.add("Engagement window coverage is below policy threshold.");
            recommendations.add(rec("send_time.coverage", "MEDIUM", "Collect broader engagement-window coverage before personalized timing recommendations."));
        }

        String sendClassification = normalize(asString(signals.get("sendClassification")));
        if ("TRANSACTIONAL".equals(sendClassification) && !bool(guardrails.get("allowTransactionalSendTimePolicy"))) {
            risk += 45;
            blockedReasons.add("Transactional sends require a separate send-time policy.");
            recommendations.add(rec("send_time.transactional_policy", "BLOCKER", "Use a transactional-specific timing policy before evaluating transactional sends."));
        }
        if ("COMMERCIAL".equals(sendClassification) && bool(signals.get("usesTransactionalEngagementData")) && !bool(guardrails.get("allowTransactionalDataForCommercial"))) {
            risk += 45;
            blockedReasons.add("Commercial STO cannot use transactional engagement data under this policy.");
            recommendations.add(rec("send_time.data_separation", "BLOCKER", "Separate commercial and transactional engagement data before STO evaluation."));
        }
        if (bool(signals.get("autoApplyRequested"))) {
            risk += 50;
            blockedReasons.add("Send-time optimization cannot auto-apply launch timing in this governance slice.");
            recommendations.add(rec("send_time.manual_review", "BLOCKER", "Require human approval before any launch-time change."));
        }
        if (bool(signals.get("changesLaunchTiming"))) {
            risk += requireSendTimeGate(signals, "quietHoursGatePassed", "Quiet-hours policy gate has not passed.", blockedReasons, recommendations);
            risk += requireSendTimeGate(signals, "approvalGatePassed", "Campaign approval gate has not passed.", blockedReasons, recommendations);
            risk += requireSendTimeGate(signals, "suppressionGatePassed", "Suppression gate has not passed.", blockedReasons, recommendations);
            risk += requireSendTimeGate(signals, "warmupGatePassed", "Warmup gate has not passed.", blockedReasons, recommendations);
            risk += requireSendTimeGate(signals, "rateLimitGatePassed", "Rate-limit gate has not passed.", blockedReasons, recommendations);
            risk += requireSendTimeGate(signals, "providerCapacityGatePassed", "Provider-capacity gate has not passed.", blockedReasons, recommendations);
            risk += requireSendTimeGate(signals, "deliverabilityGatePassed", "Deliverability gate has not passed.", blockedReasons, recommendations);
            recommendations.add(rec("send_time.approval", "HIGH", "Launch-time recommendations require human review and scheduling evidence."));
        }

        String confidenceBand = "LOW";
        if (blockedReasons.isEmpty() && !lowData && coveragePercent >= 80 && dataQualityReasons.isEmpty()) {
            confidenceBand = "HIGH";
        } else if (blockedReasons.isEmpty() && events >= (minEvents / 2) && contacts >= (minContacts / 2)) {
            confidenceBand = "MEDIUM";
        }
        assessment.put("confidenceBand", confidenceBand);
        assessment.put("fallbackMode", lowData ? "LOW_DATA_DEFAULT_SCHEDULE" : "NONE");
        assessment.put("dataQualityReasons", dataQualityReasons);
        assessment.put("lookbackDays", lookbackDays);
        return risk;
    }

    private int requireSendTimeGate(Map<String, Object> signals,
                                    String key,
                                    String blockedReason,
                                    List<String> blockedReasons,
                                    List<Map<String, Object>> recommendations) {
        if (Boolean.TRUE.equals(signals.get(key)) || "true".equalsIgnoreCase(String.valueOf(signals.get(key)))) {
            return 0;
        }
        blockedReasons.add(blockedReason);
        recommendations.add(rec("send_time." + key, "BLOCKER", blockedReason));
        return 35;
    }

    private int frequencyRisk(Map<String, Object> signals,
                              Map<String, Object> guardrails,
                              List<String> blockedReasons,
                              List<Map<String, Object>> recommendations,
                              Map<String, Object> assessment) {
        int risk = 0;
        List<String> dataQualityReasons = new ArrayList<>();
        List<String> safetyImpact = new ArrayList<>();
        double minEvents = number(guardrails.get("minEligibleSendEvents"), 1_000);
        double minContacts = number(guardrails.get("minEligibleContacts"), 500);
        double minVariants = number(guardrails.get("minFrequencyVariants"), 5);
        int lookbackDays = (int) number(guardrails.get("lookbackDays"), 28);
        double events = number(firstObject(signals.get("eligibleSendEvents"), signals.get("historicalSendEvents"), signals.get("sendEvents")), 0);
        double contacts = number(firstObject(signals.get("eligibleContacts"), signals.get("subscriberCount")), 0);
        double variants = number(firstObject(signals.get("frequencyVariantCount"), signals.get("variantCount")), 0);
        int currentCap = (int) number(firstObject(signals.get("currentFrequencyCap"), signals.get("currentCap"), guardrails.get("defaultFrequencyCap")), 0);
        int requestedCap = (int) number(firstObject(signals.get("requestedFrequencyCap"), signals.get("proposedFrequencyCap"), signals.get("recommendedFrequencyCap")), currentCap);

        if (lookbackDays < 28) {
            risk += 10;
            dataQualityReasons.add("Lookback window is shorter than 28 days.");
        }
        boolean lowData = events < minEvents || contacts < minContacts;
        if (lowData) {
            risk += 25;
            dataQualityReasons.add("Eligible send/contact history is below the configured frequency threshold.");
            recommendations.add(rec("frequency.fallback", "MEDIUM", "Keep the current cap until enough frequency history exists."));
        }
        boolean minimumVariantsMet = variants >= minVariants;
        if (!minimumVariantsMet) {
            risk += 15;
            dataQualityReasons.add("Frequency variants are below the policy threshold.");
            recommendations.add(rec("frequency.variants", "MEDIUM", "Collect enough sending-frequency variants before optimization."));
        }

        risk += blockFrequencySignal(signals, "suppressed", "Suppressed recipients cannot receive frequency increases.", safetyImpact, blockedReasons, recommendations);
        risk += blockFrequencySignal(signals, "unsubscribed", "Unsubscribed recipients cannot receive frequency increases.", safetyImpact, blockedReasons, recommendations);
        risk += blockFrequencySignal(signals, "complaintProne", "Complaint-prone cohorts cannot receive frequency increases.", safetyImpact, blockedReasons, recommendations);
        risk += blockFrequencySignal(signals, "bounceRisk", "Bounce-risk cohorts cannot receive frequency increases.", safetyImpact, blockedReasons, recommendations);
        risk += blockFrequencySignal(signals, "warmupBlocked", "Warmup-blocked senders cannot receive frequency increases.", safetyImpact, blockedReasons, recommendations);
        risk += blockFrequencySignal(signals, "overFrequencyCap", "Recipients over the current cap cannot receive frequency increases.", safetyImpact, blockedReasons, recommendations);
        risk += blockFrequencySignal(signals, "providerBlocked", "Provider-blocked sends cannot receive frequency increases.", safetyImpact, blockedReasons, recommendations);
        risk += blockFrequencySignal(signals, "rateLimitBlocked", "Rate-limit-blocked sends cannot receive frequency increases.", safetyImpact, blockedReasons, recommendations);

        double fatigueRate = number(signals.get("fatigueRate"), 0);
        double unsubscribeRate = number(signals.get("unsubscribeRate"), 0);
        double complaintRate = Math.max(number(signals.get("complaintRate"), 0), number(signals.get("complaintRatePercent"), 0) / 100);
        double bounceRate = Math.max(number(signals.get("hardBounceRate"), 0), number(signals.get("hardBounceRatePercent"), 0) / 100);
        double capUtilization = number(signals.get("frequencyCapUtilizationPercent"), 0);
        String saturationCategory = "LOW";
        if (fatigueRate >= 0.25 || unsubscribeRate >= 0.02 || complaintRate >= 0.003 || bounceRate >= 0.05 || capUtilization >= 95) {
            saturationCategory = "HIGH";
            risk += 30;
            safetyImpact.add("High saturation or negative engagement signal detected.");
            recommendations.add(rec("frequency.reduce", "HIGH", "Reduce or hold send frequency for saturated cohorts."));
        } else if (fatigueRate >= 0.15 || unsubscribeRate >= 0.01 || complaintRate >= 0.0015 || bounceRate >= 0.025 || capUtilization >= 80) {
            saturationCategory = "MEDIUM";
            risk += 15;
            safetyImpact.add("Moderate saturation signal detected.");
            recommendations.add(rec("frequency.monitor", "MEDIUM", "Hold cap increases until saturation signals improve."));
        }

        boolean cadenceChange = bool(signals.get("changesCadence")) || (requestedCap > 0 && currentCap > 0 && requestedCap != currentCap);
        boolean cadenceIncrease = bool(signals.get("cadenceIncreaseRequested"))
                || bool(signals.get("increasesFrequencyCap"))
                || (requestedCap > 0 && currentCap > 0 && requestedCap > currentCap);
        if (bool(signals.get("autoApplyRequested"))) {
            risk += 50;
            blockedReasons.add("Frequency optimization cannot auto-apply cadence changes in this governance slice.");
            safetyImpact.add("Auto-apply blocked.");
            recommendations.add(rec("frequency.manual_review", "BLOCKER", "Require human approval before any cadence change."));
        }
        if (cadenceChange || cadenceIncrease) {
            risk += requireFrequencyGate(signals, "suppressionGatePassed", "Suppression gate has not passed.", safetyImpact, blockedReasons, recommendations);
            risk += requireFrequencyGate(signals, "unsubscribeGatePassed", "Unsubscribe/preference gate has not passed.", safetyImpact, blockedReasons, recommendations);
            risk += requireFrequencyGate(signals, "warmupGatePassed", "Warmup gate has not passed.", safetyImpact, blockedReasons, recommendations);
            risk += requireFrequencyGate(signals, "rateLimitGatePassed", "Rate-limit gate has not passed.", safetyImpact, blockedReasons, recommendations);
            risk += requireFrequencyGate(signals, "providerCapacityGatePassed", "Provider-capacity gate has not passed.", safetyImpact, blockedReasons, recommendations);
            risk += requireFrequencyGate(signals, "deliverabilityGatePassed", "Deliverability gate has not passed.", safetyImpact, blockedReasons, recommendations);
            risk += requireFrequencyGate(signals, "frequencyCapGatePassed", "Current frequency-cap ledger gate has not passed.", safetyImpact, blockedReasons, recommendations);
            recommendations.add(rec("frequency.approval", "HIGH", "Cadence changes require human approval and rollback evidence."));
        }

        int recommendedCap = recommendedFrequencyCap(currentCap, requestedCap, saturationCategory, lowData, blockedReasons.isEmpty());
        String confidenceBand = "LOW";
        if (blockedReasons.isEmpty() && !lowData && minimumVariantsMet && "LOW".equals(saturationCategory)) {
            confidenceBand = "HIGH";
        } else if (blockedReasons.isEmpty() && !lowData && minimumVariantsMet) {
            confidenceBand = "MEDIUM";
        }

        assessment.put("confidenceBand", confidenceBand);
        assessment.put("fallbackMode", lowData ? "LOW_DATA_CURRENT_CAP" : "NONE");
        assessment.put("dataQualityReasons", dataQualityReasons);
        assessment.put("lookbackDays", lookbackDays);
        assessment.put("minimumVariantsMet", minimumVariantsMet);
        assessment.put("saturationCategory", saturationCategory);
        assessment.put("recommendedCap", recommendedCap);
        assessment.put("safetyImpact", safetyImpact);
        assessment.put("approvalRequired", cadenceChange || cadenceIncrease || !blockedReasons.isEmpty() || "HIGH".equals(saturationCategory));
        assessment.put("rollbackRequired", cadenceChange || cadenceIncrease || bool(signals.get("autoApplyRequested")));
        return risk;
    }

    private int blockFrequencySignal(Map<String, Object> signals,
                                     String key,
                                     String blockedReason,
                                     List<String> safetyImpact,
                                     List<String> blockedReasons,
                                     List<Map<String, Object>> recommendations) {
        if (!bool(signals.get(key))) {
            return 0;
        }
        blockedReasons.add(blockedReason);
        safetyImpact.add(blockedReason);
        recommendations.add(rec("frequency." + key, "BLOCKER", blockedReason));
        return 40;
    }

    private int requireFrequencyGate(Map<String, Object> signals,
                                     String key,
                                     String blockedReason,
                                     List<String> safetyImpact,
                                     List<String> blockedReasons,
                                     List<Map<String, Object>> recommendations) {
        if (bool(signals.get(key))) {
            return 0;
        }
        blockedReasons.add(blockedReason);
        safetyImpact.add(blockedReason);
        recommendations.add(rec("frequency." + key, "BLOCKER", blockedReason));
        return 35;
    }

    private int recommendedFrequencyCap(int currentCap,
                                        int requestedCap,
                                        String saturationCategory,
                                        boolean lowData,
                                        boolean noBlockers) {
        int baseline = currentCap > 0 ? currentCap : Math.max(1, requestedCap);
        if (lowData || !noBlockers) {
            return baseline;
        }
        if ("HIGH".equals(saturationCategory)) {
            return Math.max(1, baseline - 1);
        }
        if ("MEDIUM".equals(saturationCategory)) {
            return baseline;
        }
        return requestedCap > 0 ? Math.min(Math.max(baseline, requestedCap), baseline + 1) : baseline;
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

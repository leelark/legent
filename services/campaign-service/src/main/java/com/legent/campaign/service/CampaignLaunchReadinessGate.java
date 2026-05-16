package com.legent.campaign.service;

import com.legent.campaign.client.DeliverabilityReadinessClient;
import com.legent.campaign.client.DeliveryReadinessClient;
import com.legent.campaign.client.ReadinessDependencyException;
import com.legent.campaign.domain.Campaign;
import com.legent.campaign.dto.CampaignLaunchDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CampaignLaunchReadinessGate {

    private static final long COMPLAINT_BLOCK_THRESHOLD = 10;
    private static final long HARD_BOUNCE_BLOCK_THRESHOLD = 50;
    private static final double WARMUP_COMPLAINT_RATE_BLOCK_THRESHOLD = 0.003;
    private static final double WARMUP_BOUNCE_RATE_BLOCK_THRESHOLD = 0.05;

    private final DeliverabilityReadinessClient deliverabilityClient;
    private final DeliveryReadinessClient deliveryClient;

    public GateResult evaluate(Campaign campaign) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<CampaignLaunchDto.LaunchRecommendation> recommendations = new ArrayList<>();
        List<CampaignLaunchDto.LaunchStepResult> steps = new ArrayList<>();
        Map<String, Object> details = new LinkedHashMap<>();

        String tenantId = campaign.getTenantId();
        String workspaceId = campaign.getWorkspaceId();
        String senderDomain = domainFromEmail(campaign.getSenderEmail());
        String sendingDomain = normalizeDomain(campaign.getSendingDomain());
        String providerId = normalizeValue(campaign.getProviderId());

        details.put("senderDomain", senderDomain);
        details.put("sendingDomain", sendingDomain);
        details.put("providerId", providerId);

        if (sendingDomain == null) {
            blockers.add("Authoritative deliverability checks require a sending domain.");
            recommendations.add(recommend("authoritative.deliverability.domain", "BLOCKER", "Select sending domain",
                    "A verified sending domain is required before service-backed launch checks can pass.", false));
        } else {
            evaluateAuthentication(tenantId, workspaceId, sendingDomain, blockers, recommendations, details);
            evaluateSuppressionHealth(tenantId, workspaceId, blockers, warnings, recommendations, details);
        }

        if (providerId == null) {
            blockers.add("Delivery provider is required before launch readiness can verify warmup and capacity.");
            recommendations.add(recommend("authoritative.delivery.provider", "BLOCKER", "Select delivery provider",
                    "Choose a provider so delivery-service can verify warmup state and provider capacity.", false));
        } else if (sendingDomain != null) {
            evaluateWarmup(tenantId, workspaceId, sendingDomain, providerId, blockers, warnings, recommendations, details);
            evaluateProviderCapacity(tenantId, workspaceId, sendingDomain, providerId, blockers, warnings, recommendations, details);
        }

        boolean blocked = !blockers.isEmpty();
        boolean warned = !warnings.isEmpty();
        steps.add(step(
                "authoritative_launch_gate",
                "Authoritative Launch Gate",
                blocked ? "BLOCKED" : warned ? "WARN" : "PASS",
                blocked ? 0 : warned ? 12 : 20,
                blocked ? "Service-backed launch blockers found."
                        : warned ? "Service-backed launch checks passed with warnings."
                        : "DNS/auth, suppression, warmup, and provider capacity checks passed.",
                details
        ));

        return new GateResult(blockers, warnings, recommendations, steps, details);
    }

    private void evaluateAuthentication(String tenantId,
                                        String workspaceId,
                                        String sendingDomain,
                                        List<String> blockers,
                                        List<CampaignLaunchDto.LaunchRecommendation> recommendations,
                                        Map<String, Object> details) {
        try {
            List<DeliverabilityReadinessClient.AuthCheck> checks = deliverabilityClient.authChecks(tenantId, workspaceId);
            DeliverabilityReadinessClient.AuthCheck auth = checks.stream()
                    .filter(check -> sendingDomain.equals(normalizeDomain(check.domain())))
                    .findFirst()
                    .orElse(null);
            if (auth == null) {
                blockers.add("Sending domain " + sendingDomain + " is not registered in deliverability authentication checks.");
                recommendations.add(recommend("authoritative.auth.registration", "BLOCKER", "Register sending domain",
                        "Register and verify the sending domain in deliverability-service before launch.", false));
                details.put("auth", Map.of("domain", sendingDomain, "registered", false));
                return;
            }

            Map<String, Object> authDetails = new LinkedHashMap<>();
            authDetails.put("domainId", auth.domainId());
            authDetails.put("domain", auth.domain());
            authDetails.put("status", auth.status());
            authDetails.put("spf", auth.spf());
            authDetails.put("dkim", auth.dkim());
            authDetails.put("dmarc", auth.dmarc());
            authDetails.put("reverseDns", auth.reverseDns());
            authDetails.put("lastVerifiedAt", auth.lastVerifiedAt());
            details.put("auth", authDetails);

            if (!"VERIFIED".equals(auth.status())) {
                blockers.add("Sending domain " + sendingDomain + " must be VERIFIED in deliverability-service before launch.");
            }
            List<String> missing = new ArrayList<>();
            if (!auth.spf()) {
                missing.add("SPF");
            }
            if (!auth.dkim()) {
                missing.add("DKIM");
            }
            if (!auth.dmarc()) {
                missing.add("DMARC");
            }
            if (!missing.isEmpty()) {
                blockers.add("Sending domain " + sendingDomain + " is missing verified DNS authentication: " + String.join(", ", missing) + ".");
            }
            if (!"VERIFIED".equals(auth.status()) || !missing.isEmpty()) {
                recommendations.add(recommend("authoritative.auth.dns", "BLOCKER", "Verify DNS authentication",
                        "SPF, DKIM, DMARC, and sender-domain verification must pass in deliverability-service.", false));
            }
        } catch (ReadinessDependencyException e) {
            blockers.add("Deliverability authentication check unavailable: " + e.getMessage() + ".");
            recommendations.add(recommend("authoritative.auth.unavailable", "BLOCKER", "Restore authentication checks",
                    "Campaign launch fails closed until deliverability-service authentication checks are reachable and configured.", false));
            details.put("auth", Map.of("available", false, "reason", e.getMessage()));
        }
    }

    private void evaluateSuppressionHealth(String tenantId,
                                           String workspaceId,
                                           List<String> blockers,
                                           List<String> warnings,
                                           List<CampaignLaunchDto.LaunchRecommendation> recommendations,
                                           Map<String, Object> details) {
        try {
            DeliverabilityReadinessClient.SuppressionHealth health = deliverabilityClient.suppressionHealth(tenantId, workspaceId);
            details.put("suppressionHealth", Map.of(
                    "total", health.total(),
                    "complaints", health.complaints(),
                    "hardBounces", health.hardBounces(),
                    "unsubscribes", health.unsubscribes(),
                    "generatedAt", health.generatedAt()
            ));
            if (health.complaints() >= COMPLAINT_BLOCK_THRESHOLD) {
                blockers.add("Suppression health is blocked: complaint suppressions are at or above " + COMPLAINT_BLOCK_THRESHOLD + ".");
            } else if (health.complaints() > 0) {
                warnings.add("Complaint suppressions are present; keep launch volume controlled.");
            }
            if (health.hardBounces() >= HARD_BOUNCE_BLOCK_THRESHOLD) {
                blockers.add("Suppression health is blocked: hard-bounce suppressions are at or above " + HARD_BOUNCE_BLOCK_THRESHOLD + ".");
            } else if (health.hardBounces() > 0) {
                warnings.add("Hard-bounce suppressions are present; verify list hygiene before increasing volume.");
            }
            if (health.complaints() >= COMPLAINT_BLOCK_THRESHOLD || health.hardBounces() >= HARD_BOUNCE_BLOCK_THRESHOLD) {
                recommendations.add(recommend("authoritative.suppression.health", "BLOCKER", "Remediate suppression health",
                        "Inspect complaint and hard-bounce sources before launching more mail.", false));
            }
        } catch (ReadinessDependencyException e) {
            blockers.add("Suppression health check unavailable: " + e.getMessage() + ".");
            recommendations.add(recommend("authoritative.suppression.unavailable", "BLOCKER", "Restore suppression checks",
                    "Campaign launch fails closed until deliverability-service suppression data is reachable.", false));
            details.put("suppressionHealth", Map.of("available", false, "reason", e.getMessage()));
        }
    }

    private void evaluateWarmup(String tenantId,
                                String workspaceId,
                                String sendingDomain,
                                String providerId,
                                List<String> blockers,
                                List<String> warnings,
                                List<CampaignLaunchDto.LaunchRecommendation> recommendations,
                                Map<String, Object> details) {
        try {
            DeliveryReadinessClient.WarmupStatus status = deliveryClient.warmupStatus(tenantId, workspaceId);
            DeliveryReadinessClient.WarmupStateSnapshot state = status.states().stream()
                    .filter(candidate -> sendingDomain.equals(normalizeDomain(candidate.senderDomain()))
                            && providerId.equals(normalizeValue(candidate.providerId())))
                    .findFirst()
                    .orElse(null);
            Map<String, Object> warmupDetails = new LinkedHashMap<>();
            warmupDetails.put("activeProviders", status.activeProviders());
            warmupDetails.put("healthyProviders", status.healthyProviders());
            warmupDetails.put("degradedProviders", status.degradedProviders());
            warmupDetails.put("unhealthyProviders", status.unhealthyProviders());
            warmupDetails.put("rampStage", status.rampStage());
            warmupDetails.put("allowedVolume", status.allowedVolume());
            warmupDetails.put("rollbackReason", status.rollbackReason());
            warmupDetails.put("nextIncreaseTime", status.nextIncreaseTime());

            if (state == null) {
                blockers.add("Warmup state for " + sendingDomain + " on provider " + providerId + " is unavailable.");
                recommendations.add(recommend("authoritative.warmup.state", "BLOCKER", "Initialize warmup state",
                        "Delivery-service must expose warmup state for the sending domain and provider before launch.", false));
                warmupDetails.put("matched", false);
                details.put("warmup", warmupDetails);
                return;
            }

            warmupDetails.put("matched", true);
            warmupDetails.put("stage", state.stage());
            warmupDetails.put("hourlyLimit", state.hourlyLimit());
            warmupDetails.put("dailyLimit", state.dailyLimit());
            warmupDetails.put("sentThisHour", state.sentThisHour());
            warmupDetails.put("sentToday", state.sentToday());
            warmupDetails.put("bounceRate", state.bounceRate());
            warmupDetails.put("complaintRate", state.complaintRate());
            warmupDetails.put("stateRollbackReason", state.rollbackReason());
            warmupDetails.put("nextIncreaseAt", state.nextIncreaseAt());
            details.put("warmup", warmupDetails);

            if (state.stage() == null || state.stage().isBlank()) {
                blockers.add("Warmup stage for " + sendingDomain + " on provider " + providerId + " is unavailable.");
            }
            if (state.rollbackReason() != null && !state.rollbackReason().isBlank()) {
                blockers.add("Warmup is rolled back for " + sendingDomain + " on provider " + providerId + ": " + state.rollbackReason() + ".");
            }
            if (state.hourlyLimit() <= 0 || state.dailyLimit() <= 0) {
                blockers.add("Warmup limits for " + sendingDomain + " on provider " + providerId + " are not positive.");
            }
            if (state.hourlyLimit() > 0 && state.sentThisHour() >= state.hourlyLimit()) {
                blockers.add("Warmup hourly cap is exhausted for " + sendingDomain + " on provider " + providerId + ".");
            }
            if (state.dailyLimit() > 0 && state.sentToday() >= state.dailyLimit()) {
                blockers.add("Warmup daily cap is exhausted for " + sendingDomain + " on provider " + providerId + ".");
            }
            if (state.complaintRate() >= WARMUP_COMPLAINT_RATE_BLOCK_THRESHOLD) {
                blockers.add("Warmup complaint rate is too high for launch.");
            }
            if (state.bounceRate() >= WARMUP_BOUNCE_RATE_BLOCK_THRESHOLD) {
                blockers.add("Warmup bounce rate is too high for launch.");
            }
            if (status.degradedProviders() > 0 || status.unhealthyProviders() > 0) {
                warnings.add("Delivery warmup has degraded or unhealthy providers in the workspace.");
            }
        } catch (ReadinessDependencyException e) {
            blockers.add("Warmup readiness check unavailable: " + e.getMessage() + ".");
            recommendations.add(recommend("authoritative.warmup.unavailable", "BLOCKER", "Restore warmup checks",
                    "Campaign launch fails closed until delivery-service warmup readiness is reachable.", false));
            details.put("warmup", Map.of("available", false, "reason", e.getMessage()));
        }
    }

    private void evaluateProviderCapacity(String tenantId,
                                          String workspaceId,
                                          String sendingDomain,
                                          String providerId,
                                          List<String> blockers,
                                          List<String> warnings,
                                          List<CampaignLaunchDto.LaunchRecommendation> recommendations,
                                          Map<String, Object> details) {
        try {
            DeliveryReadinessClient.ProviderCapacityDecision decision = deliveryClient.evaluateProviderCapacity(
                    tenantId, workspaceId, providerId, sendingDomain, 0);
            Map<String, Object> capacityDetails = new LinkedHashMap<>();
            capacityDetails.put("providerId", decision.providerId());
            capacityDetails.put("throttleState", decision.throttleState());
            capacityDetails.put("recommendedPerMinute", decision.recommendedPerMinute());
            capacityDetails.put("recommendedPerSecond", decision.recommendedPerSecond());
            capacityDetails.put("reason", decision.reason());
            capacityDetails.put("evaluatedAt", decision.evaluatedAt());
            details.put("providerCapacity", capacityDetails);
            if (decision.throttleState() == null || decision.throttleState().isBlank()) {
                blockers.add("Provider capacity decision did not include a throttle state.");
            } else if ("BLOCKED".equals(decision.throttleState())) {
                blockers.add("Provider capacity blocks launch for provider " + providerId + ": " + defaultReason(decision.reason()) + ".");
            } else if ("THROTTLED".equals(decision.throttleState()) || "CAUTIOUS".equals(decision.throttleState())) {
                warnings.add("Provider capacity is " + decision.throttleState().toLowerCase(Locale.ROOT)
                        + " for provider " + providerId + ": " + defaultReason(decision.reason()) + ".");
            }
            if (decision.recommendedPerMinute() <= 0) {
                blockers.add("Provider capacity did not return positive per-minute capacity for provider " + providerId + ".");
            }
            if ("BLOCKED".equals(decision.throttleState()) || decision.recommendedPerMinute() <= 0) {
                recommendations.add(recommend("authoritative.provider.capacity", "BLOCKER", "Restore provider capacity",
                        "Resolve provider health, capacity profile, or backpressure before launch.", false));
            }
        } catch (ReadinessDependencyException e) {
            blockers.add("Provider capacity check unavailable: " + e.getMessage() + ".");
            recommendations.add(recommend("authoritative.provider.unavailable", "BLOCKER", "Restore provider capacity checks",
                    "Campaign launch fails closed until delivery-service provider capacity is reachable.", false));
            details.put("providerCapacity", Map.of("available", false, "reason", e.getMessage()));
        }
    }

    private String defaultReason(String reason) {
        return reason == null || reason.isBlank() ? "no reason supplied" : reason;
    }

    private CampaignLaunchDto.LaunchStepResult step(String key,
                                                    String label,
                                                    String status,
                                                    int score,
                                                    String message,
                                                    Map<String, Object> details) {
        return CampaignLaunchDto.LaunchStepResult.builder()
                .key(key)
                .label(label)
                .status(status)
                .score(score)
                .message(message)
                .details(details)
                .build();
    }

    private CampaignLaunchDto.LaunchRecommendation recommend(String key,
                                                             String severity,
                                                             String title,
                                                             String detail,
                                                             boolean autoFix) {
        return CampaignLaunchDto.LaunchRecommendation.builder()
                .key(key)
                .severity(severity)
                .title(title)
                .detail(detail)
                .autoFixAvailable(autoFix)
                .build();
    }

    private String domainFromEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String trimmed = email.trim();
        int at = trimmed.lastIndexOf('@');
        if (at <= 0 || at != trimmed.indexOf('@') || at == trimmed.length() - 1) {
            return null;
        }
        return normalizeDomain(trimmed.substring(at + 1));
    }

    private String normalizeDomain(String domain) {
        String normalized = normalizeValue(domain);
        if (normalized == null) {
            return null;
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    public record GateResult(
            List<String> blockers,
            List<String> warnings,
            List<CampaignLaunchDto.LaunchRecommendation> recommendations,
            List<CampaignLaunchDto.LaunchStepResult> steps,
            Map<String, Object> checks
    ) {
        public static GateResult empty() {
            return new GateResult(List.of(), List.of(), List.of(), List.of(), Map.of());
        }

        public boolean blocked() {
            return blockers != null && !blockers.isEmpty();
        }
    }
}

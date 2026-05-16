package com.legent.campaign.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.CampaignBudget;
import com.legent.campaign.domain.CampaignFrequencyPolicy;
import com.legent.campaign.domain.CampaignLaunchPlan;
import com.legent.campaign.domain.CampaignLaunchStep;
import com.legent.campaign.dto.CampaignDto;
import com.legent.campaign.dto.CampaignEngineDto;
import com.legent.campaign.dto.CampaignLaunchDto;
import com.legent.campaign.dto.SendJobDto;
import com.legent.campaign.repository.CampaignBudgetRepository;
import com.legent.campaign.repository.CampaignFrequencyPolicyRepository;
import com.legent.campaign.repository.CampaignLaunchPlanRepository;
import com.legent.campaign.repository.CampaignLaunchStepRepository;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.common.util.IdGenerator;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CampaignLaunchOrchestrationService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final CampaignRepository campaignRepository;
    private final CampaignLaunchPlanRepository launchPlanRepository;
    private final CampaignLaunchStepRepository launchStepRepository;
    private final CampaignBudgetRepository budgetRepository;
    private final CampaignFrequencyPolicyRepository frequencyPolicyRepository;
    private final CampaignEngineService campaignEngineService;
    private final CampaignWorkflowService workflowService;
    private final CampaignService campaignService;
    private final OrchestrationService orchestrationService;
    private final CampaignLaunchReadinessGate launchReadinessGate;
    private final ObjectMapper objectMapper;

    @Transactional
    public CampaignLaunchDto.LaunchPlanResponse preview(CampaignLaunchDto.LaunchPlanRequest request) {
        Campaign campaign = findCampaign(request.getCampaignId());
        Readiness readiness = evaluate(campaign, request);
        CampaignLaunchPlan plan = upsertPlan(request, readiness, CampaignLaunchPlan.LaunchPlanStatus.PREVIEWED);
        replaceSteps(plan, readiness.steps());
        return toResponse(plan, readiness, "Readiness scan complete.", Map.of());
    }

    @Transactional(readOnly = true)
    public CampaignLaunchDto.LaunchPlanResponse readiness(String campaignId) {
        Campaign campaign = findCampaign(campaignId);
        CampaignLaunchDto.LaunchPlanRequest request = CampaignLaunchDto.LaunchPlanRequest.builder()
                .campaignId(campaignId)
                .idempotencyKey("readiness:" + campaignId + ":" + IdGenerator.newIdempotencyKey())
                .action(CampaignLaunchDto.LaunchAction.PREVIEW)
                .build();
        Readiness readiness = evaluate(campaign, request);
        return CampaignLaunchDto.LaunchPlanResponse.builder()
                .campaignId(campaignId)
                .idempotencyKey(request.getIdempotencyKey())
                .status(readiness.blocked() ? "BLOCKED" : "READY")
                .readinessScore(readiness.score())
                .blockerCount(readiness.blockers().size())
                .warningCount(readiness.warnings().size())
                .primaryAction(readiness.primaryAction())
                .message(readiness.blocked() ? "Resolve blockers before launch." : "Campaign is ready for controlled execution.")
                .affectedResourceIds(Map.of("campaignId", campaignId))
                .launchControls(launchControls(request))
                .blockers(readiness.blockers())
                .warnings(readiness.warnings())
                .recommendations(readiness.recommendations())
                .steps(readiness.steps())
                .build();
    }

    @Transactional
    public CampaignLaunchDto.LaunchPlanResponse execute(CampaignLaunchDto.LaunchPlanRequest request) {
        CampaignLaunchPlan existing = launchPlanRepository
                .findByTenantIdAndWorkspaceIdAndIdempotencyKeyAndDeletedAtIsNull(
                        TenantContext.requireTenantId(),
                        TenantContext.requireWorkspaceId(),
                        request.getIdempotencyKey().trim())
                .orElse(null);
        if (existing != null && existing.getStatus() == CampaignLaunchPlan.LaunchPlanStatus.EXECUTED) {
            return responseFromPersisted(existing, "Launch action already executed for this idempotency key.");
        }

        Campaign campaign = findCampaign(request.getCampaignId());
        CampaignLaunchDto.LaunchAction action = request.getAction() == null
                ? CampaignLaunchDto.LaunchAction.AUTO
                : request.getAction();

        if (action == CampaignLaunchDto.LaunchAction.SAFE_FIX) {
            applySafeFixes(campaign);
            campaign = campaignRepository.save(campaign);
        }

        Readiness readiness = evaluate(campaign, request);
        CampaignLaunchPlan plan = upsertPlan(request, readiness, readiness.blocked()
                ? CampaignLaunchPlan.LaunchPlanStatus.BLOCKED
                : CampaignLaunchPlan.LaunchPlanStatus.READY);
        replaceSteps(plan, readiness.steps());

        if (readiness.blocked() && action != CampaignLaunchDto.LaunchAction.SAFE_FIX
                && action != CampaignLaunchDto.LaunchAction.SUBMIT_APPROVAL) {
            return toResponse(plan, readiness, "Launch blocked. Resolve required items before execution.", Map.of("campaignId", campaign.getId()));
        }

        Map<String, Object> affected = new LinkedHashMap<>();
        affected.put("campaignId", campaign.getId());
        String message = "Readiness scan complete.";
        boolean executed = false;

        switch (action) {
            case SAFE_FIX -> {
                message = "Safe campaign defaults applied.";
                executed = true;
            }
            case SUBMIT_APPROVAL -> {
                if (campaign.isApprovalRequired() && campaign.getStatus() != Campaign.CampaignStatus.APPROVED) {
                    try {
                        var approval = workflowService.submitForApproval(campaign.getTenantId(), campaign.getId(), "Submitted from Launch Command Center");
                        affected.put("approvalId", approval.getId());
                        message = "Campaign submitted for approval.";
                    } catch (ConflictException conflict) {
                        message = "Campaign already has a pending approval.";
                    }
                    executed = true;
                } else {
                    message = "Approval is not required for this campaign.";
                }
            }
            case SCHEDULE -> {
                if (request.getScheduledAt() == null) {
                    throw new ValidationException("scheduledAt", "scheduledAt is required for launch scheduling");
                }
                CampaignDto.Response scheduled = campaignService.schedule(campaign.getId(),
                        CampaignDto.ScheduleRequest.builder().scheduledAt(request.getScheduledAt()).build());
                affected.put("scheduledCampaignId", scheduled.getId());
                message = "Campaign scheduled.";
                executed = true;
            }
            case LAUNCH -> {
                if (!Boolean.TRUE.equals(request.getConfirmLaunch())) {
                    plan.setStatus(CampaignLaunchPlan.LaunchPlanStatus.READY);
                    plan.setPrimaryAction("CONFIRM_LAUNCH");
                    launchPlanRepository.save(plan);
                    return toResponse(plan, readiness, "Campaign is ready. Confirm launch to queue the send.", affected);
                }
                SendJobDto.TriggerRequest trigger = new SendJobDto.TriggerRequest();
                trigger.setScheduledAt(request.getScheduledAt());
                trigger.setTriggerSource("LAUNCH_COMMAND_CENTER");
                trigger.setTriggerReference(plan.getId());
                trigger.setIdempotencyKey("launch:" + request.getIdempotencyKey().trim());
                var job = orchestrationService.triggerSend(campaign.getId(), trigger);
                affected.put("sendJobId", job.getId());
                message = request.getScheduledAt() == null ? "Campaign send queued." : "Campaign send scheduled.";
                executed = true;
            }
            case AUTO -> {
                if (campaign.isApprovalRequired() && campaign.getStatus() == Campaign.CampaignStatus.DRAFT) {
                    try {
                        var approval = workflowService.submitForApproval(campaign.getTenantId(), campaign.getId(), "Submitted from Launch Command Center");
                        affected.put("approvalId", approval.getId());
                        message = "Campaign submitted for approval.";
                        executed = true;
                    } catch (ConflictException conflict) {
                        message = "Campaign already has a pending approval.";
                    }
                } else if (request.getScheduledAt() != null && campaign.getStatus() == Campaign.CampaignStatus.APPROVED) {
                    CampaignDto.Response scheduled = campaignService.schedule(campaign.getId(),
                            CampaignDto.ScheduleRequest.builder().scheduledAt(request.getScheduledAt()).build());
                    affected.put("scheduledCampaignId", scheduled.getId());
                    message = "Campaign scheduled.";
                    executed = true;
                } else {
                    message = "Campaign is ready for launch confirmation.";
                }
            }
            case PREVIEW -> message = "Readiness scan complete.";
        }

        if (executed) {
            plan.setStatus(CampaignLaunchPlan.LaunchPlanStatus.EXECUTED);
            plan.setExecutedAt(Instant.now());
            plan.setAuditId(TenantContext.getRequestId() == null ? IdGenerator.newIdempotencyKey() : TenantContext.getRequestId());
            plan.setSummaryJson(writeJson(Map.of("affectedResourceIds", affected, "message", message)));
            plan = launchPlanRepository.save(plan);
        }
        return toResponse(plan, readiness, message, affected);
    }

    private Readiness evaluate(Campaign campaign, CampaignLaunchDto.LaunchPlanRequest request) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<CampaignLaunchDto.LaunchRecommendation> recommendations = new ArrayList<>();
        List<CampaignLaunchDto.LaunchStepResult> steps = new ArrayList<>();

        int score = 0;
        score += audienceScore(campaign, blockers, warnings, recommendations, steps);
        score += contentScore(campaign, blockers, warnings, recommendations, steps);
        score += deliverabilityScore(campaign, blockers, warnings, recommendations, steps);
        score += deliveryScore(campaign, warnings, recommendations, steps);
        CampaignLaunchReadinessGate.GateResult authoritativeGate = launchReadinessGate.evaluate(campaign);
        addUnique(blockers, authoritativeGate.blockers());
        addUnique(warnings, authoritativeGate.warnings());
        recommendations.addAll(authoritativeGate.recommendations());
        steps.addAll(authoritativeGate.steps());
        score += governanceScore(campaign, request, blockers, warnings, recommendations, steps);
        evaluateLaunchControls(campaign, request, blockers, warnings, recommendations, steps);

        CampaignEngineDto.SendPreflightReport preflight = campaignEngineService.preflight(campaign.getId(), false);
        if (preflight.getErrors() != null && !preflight.getErrors().isEmpty()) {
            addUnique(blockers, preflight.getErrors());
        }
        if (preflight.getWarnings() != null && !preflight.getWarnings().isEmpty()) {
            addUnique(warnings, preflight.getWarnings());
        }
        if (preflight.getErrors() != null && !preflight.getErrors().isEmpty()) {
            score = Math.min(score, 64);
        }
        if (authoritativeGate.blocked()) {
            score = Math.min(score, 64);
        }

        String primaryAction = primaryAction(campaign, blockers, request);
        return new Readiness(
                Math.max(0, Math.min(100, score)),
                blockers,
                warnings,
                recommendations,
                steps,
                primaryAction
        );
    }

    private void addUnique(List<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .filter(value -> !target.contains(value))
                .forEach(target::add);
    }

    private int audienceScore(Campaign campaign,
                              List<String> blockers,
                              List<String> warnings,
                              List<CampaignLaunchDto.LaunchRecommendation> recommendations,
                              List<CampaignLaunchDto.LaunchStepResult> steps) {
        int rules = campaign.getAudiences() == null ? 0 : campaign.getAudiences().size();
        if (rules == 0) {
            blockers.add("At least one audience list or segment is required.");
            recommendations.add(recommend("audience.required", "BLOCKER", "Select launch audience",
                    "Attach a list or segment before approval, scheduling, or send.", false));
            steps.add(step("audience", "Audience", "BLOCKED", 0, "No audience selected", Map.of("rules", rules)));
            return 0;
        }
        steps.add(step("audience", "Audience", "PASS", 20, "Audience rules ready", Map.of("rules", rules)));
        return 20;
    }

    private int contentScore(Campaign campaign,
                             List<String> blockers,
                             List<String> warnings,
                             List<CampaignLaunchDto.LaunchRecommendation> recommendations,
                             List<CampaignLaunchDto.LaunchStepResult> steps) {
        boolean hasContent = campaign.getContentId() != null && !campaign.getContentId().isBlank();
        boolean hasSubject = campaign.getSubject() != null && !campaign.getSubject().isBlank();
        if (!hasContent) {
            blockers.add("A published email template is required.");
            recommendations.add(recommend("content.template", "BLOCKER", "Attach published template",
                    "Choose an approved template so render, tracking, and compliance checks can run.", false));
            steps.add(step("content", "Creative", "BLOCKED", 0, "Missing template", Map.of()));
            return 0;
        }
        if (!hasSubject) {
            warnings.add("Campaign subject is empty.");
            recommendations.add(recommend("content.subject", "WARN", "Add subject line",
                    "A subject line improves review quality and prevents weak inbox presentation.", false));
            steps.add(step("content", "Creative", "WARN", 14, "Template set, subject missing", Map.of("templateId", campaign.getContentId())));
            return 14;
        }
        steps.add(step("content", "Creative", "PASS", 20, "Template and subject ready", Map.of("templateId", campaign.getContentId())));
        return 20;
    }

    private int deliverabilityScore(Campaign campaign,
                                    List<String> blockers,
                                    List<String> warnings,
                                    List<CampaignLaunchDto.LaunchRecommendation> recommendations,
                                    List<CampaignLaunchDto.LaunchStepResult> steps) {
        boolean hasSender = campaign.getSenderEmail() != null && !campaign.getSenderEmail().isBlank();
        String senderDomain = domainFromEmail(campaign.getSenderEmail());
        String sendingDomain = normalizeDomain(campaign.getSendingDomain());
        if (!hasSender) {
            blockers.add("Sender email is required for compliance and authentication alignment.");
            recommendations.add(recommend("deliverability.sender", "BLOCKER", "Set sender email",
                    "Configure a sender profile or from address before launch.", false));
            steps.add(step("deliverability", "Deliverability", "BLOCKED", 0, "Missing sender", Map.of()));
            return 0;
        }
        if (senderDomain == null) {
            blockers.add("Sender email must include a valid domain before launch.");
            recommendations.add(recommend("deliverability.sender_domain", "BLOCKER", "Fix sender email domain",
                    "Use a valid from address so authentication alignment can be checked.", false));
            steps.add(step("deliverability", "Deliverability", "BLOCKED", 0, "Sender domain missing", Map.of("senderEmail", campaign.getSenderEmail())));
            return 0;
        }
        if (sendingDomain == null) {
            blockers.add("Sending domain is required before launch.");
            recommendations.add(recommend("deliverability.domain", "BLOCKER", "Select sending domain",
                    "Use a verified domain before launch so authentication alignment can be enforced.", false));
            steps.add(step("deliverability", "Deliverability", "BLOCKED", 0, "Sending domain missing", Map.of("senderEmail", campaign.getSenderEmail())));
            return 0;
        }
        if (!senderDomain.equals(sendingDomain)) {
            blockers.add("Sender email domain must match the selected sending domain before launch.");
            recommendations.add(recommend("deliverability.domain_alignment", "BLOCKER", "Align sender and sending domain",
                    "Set the sending domain to match the sender email domain before launch.", false));
            steps.add(step("deliverability", "Deliverability", "BLOCKED", 0, "Sender and sending domain mismatch",
                    Map.of("senderDomain", senderDomain, "sendingDomain", sendingDomain)));
            return 0;
        }
        steps.add(step("deliverability", "Deliverability", "PASS", 20, "Sender and domain ready",
                Map.of("senderDomain", senderDomain, "sendingDomain", sendingDomain)));
        return 20;
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
        if (domain == null || domain.isBlank()) {
            return null;
        }
        String normalized = domain.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private int deliveryScore(Campaign campaign,
                              List<String> warnings,
                              List<CampaignLaunchDto.LaunchRecommendation> recommendations,
                              List<CampaignLaunchDto.LaunchStepResult> steps) {
        boolean hasProvider = campaign.getProviderId() != null && !campaign.getProviderId().isBlank();
        if (!hasProvider) {
            warnings.add("No explicit delivery provider is selected.");
            recommendations.add(recommend("delivery.provider", "WARN", "Select provider",
                    "Choose a provider or confirm routing rules should select one automatically.", false));
            steps.add(step("delivery", "Delivery", "WARN", 12, "Provider will be selected by routing", Map.of()));
            return 12;
        }
        steps.add(step("delivery", "Delivery", "PASS", 20, "Provider selected", Map.of("providerId", campaign.getProviderId())));
        return 20;
    }

    private int governanceScore(Campaign campaign,
                                CampaignLaunchDto.LaunchPlanRequest request,
                                List<String> blockers,
                                List<String> warnings,
                                List<CampaignLaunchDto.LaunchRecommendation> recommendations,
                                List<CampaignLaunchDto.LaunchStepResult> steps) {
        if (request.getScheduledAt() != null && request.getScheduledAt().isBefore(Instant.now())) {
            blockers.add("Scheduled launch time must be in the future.");
            recommendations.add(recommend("governance.schedule", "BLOCKER", "Choose a future schedule",
                    "Scheduled sends need a future timestamp to avoid accidental immediate execution.", false));
        }
        if (campaign.isApprovalRequired() && campaign.getStatus() != Campaign.CampaignStatus.APPROVED) {
            blockers.add("Campaign approval is required before schedule or send.");
            recommendations.add(recommend("governance.approval", "BLOCKER", "Submit for approval",
                    "Approval protects launch changes and creates an auditable send lock.", true));
            steps.add(step("governance", "Governance", "BLOCKED", 0, "Approval required", Map.of("status", campaign.getStatus().name())));
            return 0;
        }
        if (campaign.getTrackingEnabled() == null || !campaign.getTrackingEnabled()) {
            warnings.add("Tracking is disabled.");
            recommendations.add(recommend("governance.tracking", "WARN", "Enable tracking",
                    "Tracking should stay enabled unless this is a compliance exception.", true));
        }
        steps.add(step("governance", "Governance", "PASS", 20, "Governance controls ready", Map.of("status", campaign.getStatus().name())));
        return 20;
    }

    private void evaluateLaunchControls(Campaign campaign,
                                        CampaignLaunchDto.LaunchPlanRequest request,
                                        List<String> blockers,
                                        List<String> warnings,
                                        List<CampaignLaunchDto.LaunchRecommendation> recommendations,
                                        List<CampaignLaunchDto.LaunchStepResult> steps) {
        List<String> controlIssues = new ArrayList<>();
        Instant effectiveLaunchTime = request.getScheduledAt() == null ? Instant.now() : request.getScheduledAt();

        evaluatePublicationCalendar(request.getPublicationCalendar(), effectiveLaunchTime, blockers, warnings, recommendations, controlIssues);
        evaluateBlackoutWindows(request.getBlackoutWindows(), effectiveLaunchTime, blockers, recommendations, controlIssues);
        evaluateDependencies(request.getDependencies(), blockers, recommendations, controlIssues);
        evaluateSendClassification(request.getSendClassification(), blockers, warnings, recommendations, controlIssues);
        evaluateBudgetGuard(campaign, request.getBudgetGuard(), blockers, warnings, recommendations, controlIssues);
        evaluateFrequencyGuard(campaign, request.getFrequencyGuard(), blockers, warnings, recommendations, controlIssues);

        boolean blocked = controlIssues.stream().anyMatch(issue -> issue.startsWith("BLOCKER:"));
        boolean warned = controlIssues.stream().anyMatch(issue -> issue.startsWith("WARN:"));
        String status = blocked ? "BLOCKED" : warned ? "WARN" : "PASS";
        steps.add(step("launch_controls", "Launch Controls", status, blocked ? 0 : warned ? 12 : 20,
                blocked ? "Calendar, dependency, or guardrail issue found."
                        : warned ? "Launch controls have warnings."
                        : "Calendar, dependencies, classifications, budget, and frequency controls ready.",
                launchControls(request)));
    }

    private void evaluatePublicationCalendar(CampaignLaunchDto.PublicationCalendar calendar,
                                             Instant effectiveLaunchTime,
                                             List<String> blockers,
                                             List<String> warnings,
                                             List<CampaignLaunchDto.LaunchRecommendation> recommendations,
                                             List<String> controlIssues) {
        if (calendar == null) {
            warnings.add("Publication calendar is not attached.");
            controlIssues.add("WARN:publication_calendar_missing");
            recommendations.add(recommend("calendar.attach", "WARN", "Attach publication calendar",
                    "A calendar prevents sends outside approved operating windows.", false));
            return;
        }
        if (calendar.getPublishAfter() != null && effectiveLaunchTime.isBefore(calendar.getPublishAfter())) {
            blockers.add("Launch is before publication calendar start.");
            controlIssues.add("BLOCKER:publication_calendar_before_start");
        }
        if (calendar.getPublishBefore() != null && effectiveLaunchTime.isAfter(calendar.getPublishBefore())) {
            blockers.add("Launch is after publication calendar end.");
            controlIssues.add("BLOCKER:publication_calendar_after_end");
        }
        if (calendar.getAllowedDays() != null && !calendar.getAllowedDays().isEmpty()) {
            ZoneId zoneId = safeZone(calendar.getTimezone());
            String day = ZonedDateTime.ofInstant(effectiveLaunchTime, zoneId).getDayOfWeek().name();
            boolean allowed = calendar.getAllowedDays().stream().map(value -> value.toUpperCase(Locale.ROOT)).anyMatch(day::equals);
            if (!allowed) {
                blockers.add("Launch day is blocked by publication calendar: " + day);
                controlIssues.add("BLOCKER:publication_calendar_day");
            }
        }
    }

    private void evaluateBlackoutWindows(List<CampaignLaunchDto.BlackoutWindow> blackoutWindows,
                                         Instant effectiveLaunchTime,
                                         List<String> blockers,
                                         List<CampaignLaunchDto.LaunchRecommendation> recommendations,
                                         List<String> controlIssues) {
        if (blackoutWindows == null || blackoutWindows.isEmpty()) {
            return;
        }
        for (CampaignLaunchDto.BlackoutWindow window : blackoutWindows) {
            if (window.getStartsAt() == null || window.getEndsAt() == null) {
                blockers.add("Blackout window must include startsAt and endsAt.");
                controlIssues.add("BLOCKER:blackout_window_invalid");
                continue;
            }
            if (!effectiveLaunchTime.isBefore(window.getStartsAt()) && !effectiveLaunchTime.isAfter(window.getEndsAt())) {
                String name = window.getName() == null || window.getName().isBlank() ? "unnamed blackout window" : window.getName();
                blockers.add("Launch time falls inside blackout window: " + name);
                controlIssues.add("BLOCKER:blackout_window_match");
                recommendations.add(recommend("calendar.blackout", "BLOCKER", "Move launch outside blackout",
                        "Choose an approved send time outside blackout windows.", false));
            }
        }
    }

    private void evaluateDependencies(List<CampaignLaunchDto.LaunchDependency> dependencies,
                                      List<String> blockers,
                                      List<CampaignLaunchDto.LaunchRecommendation> recommendations,
                                      List<String> controlIssues) {
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }
        for (CampaignLaunchDto.LaunchDependency dependency : dependencies) {
            if (!Boolean.TRUE.equals(dependency.getSatisfied())) {
                String key = dependency.getKey() == null ? "dependency" : dependency.getKey();
                blockers.add("Launch dependency is not satisfied: " + key);
                controlIssues.add("BLOCKER:dependency:" + key);
                recommendations.add(recommend("dependency." + key, "BLOCKER", "Satisfy dependency",
                        dependency.getDetail() == null ? "Resolve dependency before launch." : dependency.getDetail(), false));
            }
        }
    }

    private void evaluateSendClassification(CampaignLaunchDto.SendClassification classification,
                                            List<String> blockers,
                                            List<String> warnings,
                                            List<CampaignLaunchDto.LaunchRecommendation> recommendations,
                                            List<String> controlIssues) {
        if (classification == null) {
            warnings.add("Send classification is not selected.");
            controlIssues.add("WARN:send_classification_missing");
            recommendations.add(recommend("classification.required", "WARN", "Select send classification",
                    "Use a governed sender, delivery profile, and unsubscribe policy.", false));
            return;
        }
        if (classification.getClassificationKey() == null || classification.getClassificationKey().isBlank()) {
            warnings.add("Send classification key is empty.");
            controlIssues.add("WARN:classification_key_missing");
        }
        if (Boolean.TRUE.equals(classification.getCommercial())) {
            if (classification.getUnsubscribePolicy() == null || classification.getUnsubscribePolicy().isBlank()) {
                blockers.add("Commercial sends require an unsubscribe policy.");
                controlIssues.add("BLOCKER:unsubscribe_policy_missing");
            }
            if (Boolean.TRUE.equals(classification.getRequiresConsent()) && classification.getSenderProfileId() == null) {
                warnings.add("Consent-required send has no sender profile.");
                controlIssues.add("WARN:sender_profile_missing");
            }
        }
    }

    private void evaluateBudgetGuard(Campaign campaign,
                                     CampaignLaunchDto.BudgetGuard guard,
                                     List<String> blockers,
                                     List<String> warnings,
                                     List<CampaignLaunchDto.LaunchRecommendation> recommendations,
                                     List<String> controlIssues) {
        var budget = budgetRepository.findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(
                campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId());
        boolean enforce = guard != null && Boolean.TRUE.equals(guard.getEnforced());
        if (enforce && budget.isEmpty()) {
            blockers.add("Budget guard is enforced but campaign budget is not configured.");
            controlIssues.add("BLOCKER:budget_missing");
            return;
        }
        budget.ifPresent(current -> {
            if (CampaignBudget.STATUS_EXHAUSTED.equals(current.getStatus())) {
                blockers.add("Campaign budget is exhausted.");
                controlIssues.add("BLOCKER:budget_exhausted");
            }
            Long estimatedRecipients = guard == null ? null : guard.getEstimatedRecipients();
            if ((enforce || current.isEnforced()) && estimatedRecipients != null && estimatedRecipients > 0) {
                BigDecimal estimatedSpend = current.getCostPerSend().multiply(BigDecimal.valueOf(estimatedRecipients));
                BigDecimal projected = current.getReservedSpend().add(current.getActualSpend()).add(estimatedSpend);
                if (current.getBudgetLimit().compareTo(BigDecimal.ZERO) > 0 && projected.compareTo(current.getBudgetLimit()) > 0) {
                    blockers.add("Projected launch spend exceeds campaign budget.");
                    controlIssues.add("BLOCKER:budget_overrun");
                }
            }
            if (!current.isEnforced()) {
                warnings.add("Campaign budget is configured but not enforced.");
                controlIssues.add("WARN:budget_not_enforced");
            }
        });
        if (budget.isEmpty() && !enforce) {
            recommendations.add(recommend("budget.configure", "INFO", "Configure budget guard",
                    "Budget caps protect high-volume launch spend.", false));
        }
    }

    private void evaluateFrequencyGuard(Campaign campaign,
                                        CampaignLaunchDto.FrequencyGuard guard,
                                        List<String> blockers,
                                        List<String> warnings,
                                        List<CampaignLaunchDto.LaunchRecommendation> recommendations,
                                        List<String> controlIssues) {
        var persistedPolicy = frequencyPolicyRepository.findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(
                campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId());
        if (guard != null && Boolean.TRUE.equals(guard.getEnforceWorkspacePolicy())) {
            if (guard.getMaxSends() == null || guard.getMaxSends() < 1) {
                blockers.add("Frequency guard is enabled but maxSends is not positive.");
                controlIssues.add("BLOCKER:frequency_max_sends");
            }
            if (guard.getWindowHours() == null || guard.getWindowHours() < 1) {
                blockers.add("Frequency guard is enabled but windowHours is not positive.");
                controlIssues.add("BLOCKER:frequency_window");
            }
        }
        persistedPolicy.ifPresent(policy -> {
            if (policy.isEnabled() && (policy.getMaxSends() == null || policy.getMaxSends() < 1)) {
                blockers.add("Persisted frequency policy is enabled with invalid maxSends.");
                controlIssues.add("BLOCKER:persisted_frequency_invalid");
            }
            if (!policy.isEnabled()) {
                warnings.add("Campaign frequency policy is disabled.");
                controlIssues.add("WARN:frequency_policy_disabled");
            }
        });
        if (persistedPolicy.isEmpty() && guard == null) {
            recommendations.add(recommend("frequency.configure", "INFO", "Configure frequency cap",
                    "Frequency caps protect subscribers across campaigns and journeys.", false));
        }
    }

    private String primaryAction(Campaign campaign, List<String> blockers, CampaignLaunchDto.LaunchPlanRequest request) {
        if (!blockers.isEmpty()) {
            if (campaign.isApprovalRequired() && campaign.getStatus() == Campaign.CampaignStatus.DRAFT
                    && blockers.stream().allMatch(blocker -> blocker.toLowerCase(Locale.ROOT).contains("approval"))) {
                return "SUBMIT_APPROVAL";
            }
            return "FIX_BLOCKERS";
        }
        if (campaign.getStatus() == Campaign.CampaignStatus.APPROVED && request.getScheduledAt() != null) {
            return "SCHEDULE";
        }
        return "CONFIRM_LAUNCH";
    }

    private void applySafeFixes(Campaign campaign) {
        if (campaign.getTrackingEnabled() == null || !campaign.getTrackingEnabled()) {
            campaign.setTrackingEnabled(Boolean.TRUE);
        }
        if (campaign.getComplianceEnabled() == null || !campaign.getComplianceEnabled()) {
            campaign.setComplianceEnabled(Boolean.TRUE);
        }
        if (campaign.getTimezone() == null || campaign.getTimezone().isBlank()) {
            campaign.setTimezone("UTC");
        }
        if (campaign.getFrequencyCap() == null) {
            campaign.setFrequencyCap(0);
        }
        if (campaign.getExperimentConfig() == null || campaign.getExperimentConfig().isBlank()) {
            campaign.setExperimentConfig("{}");
        }
    }

    private Campaign findCampaign(String campaignId) {
        return campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                        TenantContext.requireTenantId(),
                        TenantContext.requireWorkspaceId(),
                        campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign", campaignId));
    }

    private CampaignLaunchPlan upsertPlan(CampaignLaunchDto.LaunchPlanRequest request,
                                          Readiness readiness,
                                          CampaignLaunchPlan.LaunchPlanStatus status) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        CampaignLaunchPlan plan = launchPlanRepository
                .findByTenantIdAndWorkspaceIdAndIdempotencyKeyAndDeletedAtIsNull(
                        tenantId, workspaceId, request.getIdempotencyKey().trim())
                .orElseGet(CampaignLaunchPlan::new);
        plan.setTenantId(tenantId);
        plan.setWorkspaceId(workspaceId);
        plan.setCampaignId(request.getCampaignId());
        plan.setIdempotencyKey(request.getIdempotencyKey().trim());
        plan.setStatus(status);
        plan.setReadinessScore(readiness.score());
        plan.setBlockerCount(readiness.blockers().size());
        plan.setWarningCount(readiness.warnings().size());
        plan.setPrimaryAction(readiness.primaryAction());
        plan.setRequestJson(writeJson(request));
        plan.setSummaryJson(writeJson(Map.of(
                "blockers", readiness.blockers(),
                "warnings", readiness.warnings(),
                "recommendations", readiness.recommendations()
        )));
        return launchPlanRepository.save(plan);
    }

    private void replaceSteps(CampaignLaunchPlan plan, List<CampaignLaunchDto.LaunchStepResult> steps) {
        launchStepRepository
                .findByTenantIdAndWorkspaceIdAndLaunchPlanIdAndDeletedAtIsNullOrderBySortOrderAsc(
                        plan.getTenantId(), plan.getWorkspaceId(), plan.getId())
                .forEach(step -> {
                    step.softDelete();
                    launchStepRepository.save(step);
                });
        for (int i = 0; i < steps.size(); i++) {
            CampaignLaunchDto.LaunchStepResult result = steps.get(i);
            CampaignLaunchStep step = new CampaignLaunchStep();
            step.setTenantId(plan.getTenantId());
            step.setWorkspaceId(plan.getWorkspaceId());
            step.setLaunchPlanId(plan.getId());
            step.setCampaignId(plan.getCampaignId());
            step.setStepKey(result.getKey());
            step.setStepLabel(result.getLabel());
            step.setStatus(CampaignLaunchStep.LaunchStepStatus.valueOf(result.getStatus()));
            step.setScore(result.getScore());
            step.setMessage(result.getMessage());
            step.setSortOrder(i);
            step.setDetailsJson(writeJson(result.getDetails() == null ? Map.of() : result.getDetails()));
            launchStepRepository.save(step);
        }
    }

    private CampaignLaunchDto.LaunchPlanResponse responseFromPersisted(CampaignLaunchPlan plan, String message) {
        List<CampaignLaunchDto.LaunchStepResult> steps = launchStepRepository
                .findByTenantIdAndWorkspaceIdAndLaunchPlanIdAndDeletedAtIsNullOrderBySortOrderAsc(
                        plan.getTenantId(), plan.getWorkspaceId(), plan.getId())
                .stream()
                .map(step -> CampaignLaunchDto.LaunchStepResult.builder()
                        .key(step.getStepKey())
                        .label(step.getStepLabel())
                        .status(step.getStatus().name())
                        .score(step.getScore())
                        .message(step.getMessage())
                        .details(readMap(step.getDetailsJson()))
                        .build())
                .toList();
        Map<String, Object> summary = readMap(plan.getSummaryJson());
        return CampaignLaunchDto.LaunchPlanResponse.builder()
                .planId(plan.getId())
                .campaignId(plan.getCampaignId())
                .idempotencyKey(plan.getIdempotencyKey())
                .status(plan.getStatus().name())
                .readinessScore(plan.getReadinessScore())
                .blockerCount(plan.getBlockerCount())
                .warningCount(plan.getWarningCount())
                .primaryAction(plan.getPrimaryAction())
                .message(message)
                .auditId(plan.getAuditId())
                .affectedResourceIds(castMap(summary.get("affectedResourceIds")))
                .launchControls(launchControlsFromRequest(plan.getRequestJson()))
                .blockers(castList(summary.get("blockers")))
                .warnings(castList(summary.get("warnings")))
                .recommendations(List.of())
                .steps(steps)
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }

    private CampaignLaunchDto.LaunchPlanResponse toResponse(CampaignLaunchPlan plan,
                                                            Readiness readiness,
                                                            String message,
                                                            Map<String, Object> affected) {
        return CampaignLaunchDto.LaunchPlanResponse.builder()
                .planId(plan.getId())
                .campaignId(plan.getCampaignId())
                .idempotencyKey(plan.getIdempotencyKey())
                .status(plan.getStatus().name())
                .readinessScore(readiness.score())
                .blockerCount(readiness.blockers().size())
                .warningCount(readiness.warnings().size())
                .primaryAction(plan.getPrimaryAction())
                .message(message)
                .auditId(plan.getAuditId())
                .affectedResourceIds(affected)
                .launchControls(launchControlsFromRequest(plan.getRequestJson()))
                .blockers(readiness.blockers())
                .warnings(readiness.warnings())
                .recommendations(readiness.recommendations())
                .steps(readiness.steps())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, Object> launchControlsFromRequest(String requestJson) {
        Map<String, Object> requestMap = readMap(requestJson);
        return Map.of(
                "publicationCalendar", controlValue(requestMap, "publicationCalendar", Map.of()),
                "blackoutWindows", controlValue(requestMap, "blackoutWindows", List.of()),
                "dependencies", controlValue(requestMap, "dependencies", List.of()),
                "sendClassification", controlValue(requestMap, "sendClassification", Map.of()),
                "budgetGuard", controlValue(requestMap, "budgetGuard", Map.of()),
                "frequencyGuard", controlValue(requestMap, "frequencyGuard", Map.of())
        );
    }

    private Object controlValue(Map<String, Object> requestMap, String key, Object defaultValue) {
        Object value = requestMap.get(key);
        return value == null ? defaultValue : value;
    }

    private Map<String, Object> launchControls(CampaignLaunchDto.LaunchPlanRequest request) {
        Map<String, Object> controls = new LinkedHashMap<>();
        controls.put("publicationCalendar", request.getPublicationCalendar());
        controls.put("blackoutWindows", request.getBlackoutWindows() == null ? List.of() : request.getBlackoutWindows());
        controls.put("dependencies", request.getDependencies() == null ? List.of() : request.getDependencies());
        controls.put("sendClassification", request.getSendClassification());
        controls.put("budgetGuard", request.getBudgetGuard());
        controls.put("frequencyGuard", request.getFrequencyGuard());
        return controls;
    }

    private ZoneId safeZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception ignored) {
            return ZoneId.of("UTC");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(Objects.toString(key), item));
            return result;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> castList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Objects::toString).toList();
        }
        return List.of();
    }

    private record Readiness(
            int score,
            List<String> blockers,
            List<String> warnings,
            List<CampaignLaunchDto.LaunchRecommendation> recommendations,
            List<CampaignLaunchDto.LaunchStepResult> steps,
            String primaryAction
    ) {
        private boolean blocked() {
            return !blockers.isEmpty();
        }
    }
}

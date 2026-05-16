package com.legent.campaign.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.CampaignBudget;
import com.legent.campaign.domain.CampaignDeadLetter;
import com.legent.campaign.domain.CampaignExperiment;
import com.legent.campaign.domain.CampaignFrequencyPolicy;
import com.legent.campaign.domain.CampaignSendLedger;
import com.legent.campaign.domain.CampaignVariant;
import com.legent.campaign.dto.CampaignEngineDto;
import com.legent.campaign.event.CampaignEventPublisher;
import com.legent.campaign.repository.CampaignBudgetRepository;
import com.legent.campaign.repository.CampaignDeadLetterRepository;
import com.legent.campaign.repository.CampaignExperimentRepository;
import com.legent.campaign.repository.CampaignFrequencyPolicyRepository;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.CampaignSendLedgerRepository;
import com.legent.campaign.repository.CampaignVariantRepository;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.common.util.IdGenerator;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignEngineService {

    private final CampaignRepository campaignRepository;
    private final CampaignExperimentRepository experimentRepository;
    private final CampaignVariantRepository variantRepository;
    private final CampaignBudgetRepository budgetRepository;
    private final CampaignFrequencyPolicyRepository frequencyPolicyRepository;
    private final CampaignSendLedgerRepository ledgerRepository;
    private final CampaignDeadLetterRepository deadLetterRepository;
    private final CampaignMetricsService metricsService;
    private final CampaignEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<CampaignEngineDto.ExperimentResponse> listExperiments(String campaignId) {
        Campaign campaign = findCampaign(campaignId);
        return experimentRepository
                .findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                        campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId())
                .stream()
                .map(this::toExperimentResponse)
                .toList();
    }

    @Transactional
    public CampaignEngineDto.ExperimentResponse createExperiment(String campaignId, CampaignEngineDto.ExperimentRequest request) {
        Campaign campaign = findCampaign(campaignId);
        validateExperimentRequest(request);
        CampaignExperiment experiment = new CampaignExperiment();
        experiment.setTenantId(campaign.getTenantId());
        experiment.setWorkspaceId(campaign.getWorkspaceId());
        experiment.setCampaignId(campaign.getId());
        applyExperimentRequest(experiment, request);
        experiment = experimentRepository.save(experiment);
        replaceVariants(campaign, experiment, request.getVariants());
        return toExperimentResponse(experiment);
    }

    @Transactional
    public CampaignEngineDto.ExperimentResponse updateExperiment(String campaignId, String experimentId, CampaignEngineDto.ExperimentRequest request) {
        Campaign campaign = findCampaign(campaignId);
        validateExperimentRequest(request);
        CampaignExperiment experiment = findExperiment(campaign, experimentId);
        applyExperimentRequest(experiment, request);
        CampaignExperiment saved = experimentRepository.save(experiment);
        replaceVariants(campaign, saved, request.getVariants());
        return toExperimentResponse(saved);
    }

    @Transactional
    public void deleteExperiment(String campaignId, String experimentId) {
        Campaign campaign = findCampaign(campaignId);
        CampaignExperiment experiment = findExperiment(campaign, experimentId);
        variantRepository.findByTenantIdAndWorkspaceIdAndExperimentIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                        campaign.getTenantId(), campaign.getWorkspaceId(), experiment.getId())
                .forEach(CampaignVariant::softDelete);
        experiment.softDelete();
        experimentRepository.save(experiment);
    }

    @Transactional
    public CampaignEngineDto.ExperimentResponse promoteWinner(String campaignId, String experimentId) {
        Campaign campaign = findCampaign(campaignId);
        CampaignExperiment experiment = findExperiment(campaign, experimentId);
        CampaignEngineDto.VariantMetricsResponse winner = metricsService
                .chooseWinner(campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId(), experiment)
                .orElseThrow(() -> new ValidationException("experiment.metrics", "No eligible winner has enough samples"));
        experiment.setWinnerVariantId(winner.getVariantId());
        experiment.setStatus(CampaignExperiment.ExperimentStatus.PROMOTED);
        experiment.setCompletedAt(Instant.now());
        variantRepository.findByTenantIdAndWorkspaceIdAndExperimentIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                        campaign.getTenantId(), campaign.getWorkspaceId(), experiment.getId())
                .forEach(variant -> {
                    variant.setWinner(variant.getId().equals(winner.getVariantId()));
                    variantRepository.save(variant);
                });
        return toExperimentResponse(experimentRepository.save(experiment));
    }

    @Transactional(readOnly = true)
    public List<CampaignEngineDto.VariantMetricsResponse> getExperimentMetrics(String campaignId, String experimentId) {
        Campaign campaign = findCampaign(campaignId);
        CampaignExperiment experiment = findExperiment(campaign, experimentId);
        return metricsService.getMetrics(campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId(), experiment);
    }

    @Transactional(readOnly = true)
    public CampaignEngineDto.ExperimentAnalysisResponse analyzeExperiment(String campaignId, String experimentId) {
        Campaign campaign = findCampaign(campaignId);
        CampaignExperiment experiment = findExperiment(campaign, experimentId);
        return metricsService.analyzeExperiment(campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId(), experiment);
    }

    @Transactional(readOnly = true)
    public CampaignEngineDto.BudgetResponse getBudget(String campaignId) {
        Campaign campaign = findCampaign(campaignId);
        return toBudgetResponse(getOrDefaultBudget(campaign));
    }

    @Transactional
    public CampaignEngineDto.BudgetResponse updateBudget(String campaignId, CampaignEngineDto.BudgetRequest request) {
        Campaign campaign = findCampaign(campaignId);
        CampaignBudget budget = budgetRepository.findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(
                        campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId())
                .orElseGet(() -> newBudget(campaign));
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            budget.setCurrency(request.getCurrency().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (request.getBudgetLimit() != null) {
            budget.setBudgetLimit(request.getBudgetLimit());
        }
        if (request.getCostPerSend() != null) {
            budget.setCostPerSend(request.getCostPerSend());
        }
        if (request.getEnforced() != null) {
            budget.setEnforced(request.getEnforced());
        }
        return toBudgetResponse(budgetRepository.save(budget));
    }

    @Transactional(readOnly = true)
    public CampaignEngineDto.FrequencyPolicyResponse getFrequencyPolicy(String campaignId) {
        Campaign campaign = findCampaign(campaignId);
        return toFrequencyResponse(getOrDefaultFrequency(campaign));
    }

    @Transactional
    public CampaignEngineDto.FrequencyPolicyResponse updateFrequencyPolicy(String campaignId, CampaignEngineDto.FrequencyPolicyRequest request) {
        Campaign campaign = findCampaign(campaignId);
        CampaignFrequencyPolicy policy = frequencyPolicyRepository.findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(
                        campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId())
                .orElseGet(() -> newFrequencyPolicy(campaign));
        if (request.getEnabled() != null) {
            policy.setEnabled(request.getEnabled());
        }
        if (request.getMaxSends() != null) {
            policy.setMaxSends(request.getMaxSends());
        }
        if (request.getWindowHours() != null) {
            policy.setWindowHours(request.getWindowHours());
        }
        if (request.getIncludeJourneys() != null) {
            policy.setIncludeJourneys(request.getIncludeJourneys());
        }
        return toFrequencyResponse(frequencyPolicyRepository.save(policy));
    }

    @Transactional(readOnly = true)
    public CampaignEngineDto.SendPreflightReport preflight(String campaignId) {
        Campaign campaign = findCampaign(campaignId);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (campaign.getContentId() == null || campaign.getContentId().isBlank()) {
            errors.add("Campaign must reference an approved published template before send.");
        }
        if (campaign.getAudiences() == null || campaign.getAudiences().isEmpty()) {
            errors.add("At least one include or exclude audience rule is required.");
        }
        validateSenderDomainAlignment(campaign, errors);
        if (campaign.isApprovalRequired() && campaign.getStatus() != Campaign.CampaignStatus.APPROVED) {
            errors.add("Campaign approval is required before send.");
        }
        CampaignBudget budget = getOrDefaultBudget(campaign);
        if (budget.isEnforced() && budget.getBudgetLimit().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Enforced campaign budget requires a positive budget limit.");
        }
        CampaignFrequencyPolicy policy = getOrDefaultFrequency(campaign);
        if (policy.isEnabled() && policy.getMaxSends() <= 0) {
            errors.add("Enabled frequency policy requires maxSends greater than zero.");
        }
        List<CampaignExperiment> experiments = experimentRepository
                .findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                        campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId());
        long activeExperiments = experiments.stream()
                .filter(experiment -> experiment.getStatus() == CampaignExperiment.ExperimentStatus.ACTIVE
                        || experiment.getStatus() == CampaignExperiment.ExperimentStatus.PROMOTED)
                .count();
        if (activeExperiments > 1) {
            errors.add("Only one active or promoted experiment may be used for a campaign send.");
        }
        experiments.forEach(experiment -> {
            List<CampaignVariant> variants = variantRepository
                    .findByTenantIdAndWorkspaceIdAndExperimentIdAndActiveTrueAndDeletedAtIsNullOrderByCreatedAtAsc(
                            campaign.getTenantId(), campaign.getWorkspaceId(), experiment.getId());
            int totalWeight = variants.stream()
                    .filter(variant -> !variant.isHoldoutVariant())
                    .map(CampaignVariant::getWeight)
                    .filter(weight -> weight != null && weight > 0)
                    .mapToInt(Integer::intValue)
                    .sum();
            if ((experiment.getStatus() == CampaignExperiment.ExperimentStatus.ACTIVE
                    || experiment.getStatus() == CampaignExperiment.ExperimentStatus.PROMOTED)
                    && variants.size() < 2 && experiment.getStatus() != CampaignExperiment.ExperimentStatus.PROMOTED) {
                warnings.add("Active experiment " + experiment.getName() + " has fewer than two active variants.");
            }
            if (totalWeight <= 0) {
                warnings.add("Experiment " + experiment.getName() + " has no weighted send variants.");
            }
        });

        String senderDomain = domainFromEmail(campaign.getSenderEmail());
        String sendingDomain = normalizeDomain(campaign.getSendingDomain());
        return CampaignEngineDto.SendPreflightReport.builder()
                .campaignId(campaign.getId())
                .sendAllowed(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .checks(Map.of(
                        "audienceRules", campaign.getAudiences() == null ? 0 : campaign.getAudiences().size(),
                        "senderDomain", senderDomain == null ? "" : senderDomain,
                        "sendingDomain", sendingDomain == null ? "" : sendingDomain,
                        "experiments", experiments.size(),
                        "budgetEnforced", budget.isEnforced(),
                        "frequencyEnabled", policy.isEnabled()))
                .build();
    }

    private void validateSenderDomainAlignment(Campaign campaign, List<String> errors) {
        String senderDomain = domainFromEmail(campaign.getSenderEmail());
        String sendingDomain = normalizeDomain(campaign.getSendingDomain());
        if (senderDomain == null) {
            errors.add("Sender email must include a valid domain before send.");
            return;
        }
        if (sendingDomain == null) {
            errors.add("Sending domain is required before send.");
            return;
        }
        if (!senderDomain.equals(sendingDomain)) {
            errors.add("Sender email domain must match the selected sending domain before send.");
        }
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

    @Transactional(readOnly = true)
    public CampaignEngineDto.ResendPlanResponse createResendPlan(String campaignId, CampaignEngineDto.ResendPlanRequest request) {
        Campaign campaign = findCampaign(campaignId);
        String mode = request.getResendMode().trim().toUpperCase(java.util.Locale.ROOT);
        long eligible = switch (mode) {
            case "FAILED_ONLY" -> ledgerRepository.countByState(
                    campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId(), CampaignSendLedger.SendState.FAILED);
            case "NOT_SENT", "SUPPRESSED_RECHECK" -> ledgerRepository.countByState(
                    campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId(), CampaignSendLedger.SendState.SUPPRESSED);
            case "ALL_REQUIRES_CONFIRMATION" -> ledgerRepository.countByState(
                    campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId(), CampaignSendLedger.SendState.SENT);
            default -> throw new ValidationException("resendMode", "Unsupported resend mode: " + mode);
        };
        boolean dangerousAll = "ALL_REQUIRES_CONFIRMATION".equals(mode);
        if (dangerousAll && !Boolean.TRUE.equals(request.getConfirmed())) {
            throw new ValidationException("confirmed", "ALL_REQUIRES_CONFIRMATION requires confirmed=true");
        }
        return CampaignEngineDto.ResendPlanResponse.builder()
                .campaignId(campaign.getId())
                .resendMode(mode)
                .requiresConfirmation(dangerousAll)
                .eligibleRecipients(eligible)
                .idempotencyKey("resend-plan:" + campaign.getId() + ":" + mode + ":" + requestKey())
                .warnings(dangerousAll ? List.of("This plan may resend to previously sent recipients.") : List.of())
                .build();
    }

    @Transactional(readOnly = true)
    public List<CampaignEngineDto.DeadLetterResponse> listDeadLetters(String jobId) {
        return deadLetterRepository.findByTenantIdAndWorkspaceIdAndJobIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                        TenantContext.requireTenantId(), TenantContext.requireWorkspaceId(), jobId)
                .stream()
                .map(this::toDeadLetterResponse)
                .toList();
    }

    @Transactional
    public CampaignEngineDto.DeadLetterResponse replayDeadLetter(String jobId, String deadLetterId) {
        CampaignDeadLetter letter = deadLetterRepository.findByTenantIdAndWorkspaceIdAndJobIdAndIdAndDeletedAtIsNull(
                        TenantContext.requireTenantId(), TenantContext.requireWorkspaceId(), jobId, deadLetterId)
                .orElseThrow(() -> new NotFoundException("CampaignDeadLetter", deadLetterId));
        if (letter.getBatchId() == null || letter.getBatchId().isBlank()) {
            throw new ValidationException("batchId", "Dead letter has no batch to replay");
        }
        letter.setStatus(CampaignDeadLetter.STATUS_REPLAYED);
        letter.setReplayedAt(Instant.now());
        letter.setRetryCount((letter.getRetryCount() == null ? 0 : letter.getRetryCount()) + 1);
        CampaignDeadLetter saved = deadLetterRepository.save(letter);
        eventPublisher.publishBatchCreated(letter.getTenantId(), letter.getJobId(), letter.getBatchId());
        return toDeadLetterResponse(saved);
    }

    @Scheduled(fixedDelayString = "${legent.campaign.experiments.evaluator-delay:900000}")
    @Transactional
    public void evaluateExperimentWinners() {
        List<CampaignExperiment> active = experimentRepository.findByStatusAndDeletedAtIsNull(CampaignExperiment.ExperimentStatus.ACTIVE);
        for (CampaignExperiment experiment : active) {
            if (!experiment.isAutoPromotion()) {
                continue;
            }
            metricsService.chooseWinner(experiment.getTenantId(), experiment.getWorkspaceId(), experiment.getCampaignId(), experiment)
                    .ifPresent(winner -> {
                        experiment.setWinnerVariantId(winner.getVariantId());
                        experiment.setStatus(CampaignExperiment.ExperimentStatus.PROMOTED);
                        experiment.setCompletedAt(Instant.now());
                        experimentRepository.save(experiment);
                        log.info("Auto-promoted campaign experiment winner campaign={} experiment={} variant={}",
                                experiment.getCampaignId(), experiment.getId(), winner.getVariantId());
                    });
        }
    }

    private void validateExperimentRequest(CampaignEngineDto.ExperimentRequest request) {
        if (request.getExperimentType() == null) {
            throw new ValidationException("experimentType", "experimentType is required");
        }
        if (request.getWinnerMetric() == CampaignExperiment.WinnerMetric.CUSTOM
                && (request.getCustomMetricName() == null || request.getCustomMetricName().isBlank())) {
            throw new ValidationException("customMetricName", "customMetricName is required when winnerMetric is CUSTOM");
        }
        if (request.getVariants() == null || request.getVariants().isEmpty()) {
            throw new ValidationException("variants", "At least one variant is required");
        }
    }

    private void applyExperimentRequest(CampaignExperiment experiment, CampaignEngineDto.ExperimentRequest request) {
        experiment.setName(request.getName().trim());
        experiment.setExperimentType(request.getExperimentType());
        experiment.setWinnerMetric(request.getWinnerMetric());
        experiment.setCustomMetricName(request.getCustomMetricName());
        experiment.setAutoPromotion(Boolean.TRUE.equals(request.getAutoPromotion()));
        experiment.setMinRecipientsPerVariant(request.getMinRecipientsPerVariant() == null ? 100 : request.getMinRecipientsPerVariant());
        experiment.setEvaluationWindowHours(request.getEvaluationWindowHours() == null ? 24 : request.getEvaluationWindowHours());
        experiment.setHoldoutPercentage(request.getHoldoutPercentage() == null ? BigDecimal.ZERO : request.getHoldoutPercentage());
        experiment.setFactors(request.getFactors() == null || request.getFactors().isBlank() ? "[]" : request.getFactors());
        experiment.setStatus(request.getStatus() == null ? CampaignExperiment.ExperimentStatus.DRAFT : request.getStatus());
    }

    private void replaceVariants(Campaign campaign, CampaignExperiment experiment, List<CampaignEngineDto.VariantRequest> variants) {
        variantRepository.findByTenantIdAndWorkspaceIdAndExperimentIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                        campaign.getTenantId(), campaign.getWorkspaceId(), experiment.getId())
                .forEach(variant -> {
                    variant.softDelete();
                    variantRepository.save(variant);
                });
        for (CampaignEngineDto.VariantRequest request : variants) {
            CampaignVariant variant = new CampaignVariant();
            variant.setTenantId(campaign.getTenantId());
            variant.setWorkspaceId(campaign.getWorkspaceId());
            variant.setCampaignId(campaign.getId());
            variant.setExperimentId(experiment.getId());
            variant.setVariantKey(request.getVariantKey().trim());
            variant.setName(request.getName().trim());
            variant.setWeight(request.getWeight() == null ? 0 : request.getWeight());
            variant.setControlVariant(Boolean.TRUE.equals(request.getControlVariant()));
            variant.setHoldoutVariant(Boolean.TRUE.equals(request.getHoldoutVariant()));
            variant.setActive(request.getActive() == null || request.getActive());
            variant.setContentId(request.getContentId());
            variant.setSubjectOverride(request.getSubjectOverride());
            variant.setMetadata(request.getMetadata() == null || request.getMetadata().isBlank() ? "{}" : request.getMetadata());
            variantRepository.save(variant);
        }
    }

    private Campaign findCampaign(String campaignId) {
        return campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                        TenantContext.requireTenantId(), TenantContext.requireWorkspaceId(), campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign", campaignId));
    }

    private String requestKey() {
        String requestId = TenantContext.getRequestId();
        return requestId == null || requestId.isBlank() ? IdGenerator.newIdempotencyKey() : requestId.trim();
    }

    private CampaignExperiment findExperiment(Campaign campaign, String experimentId) {
        return experimentRepository.findByTenantIdAndWorkspaceIdAndCampaignIdAndIdAndDeletedAtIsNull(
                        campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId(), experimentId)
                .orElseThrow(() -> new NotFoundException("CampaignExperiment", experimentId));
    }

    private CampaignBudget getOrDefaultBudget(Campaign campaign) {
        return budgetRepository.findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(
                        campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId())
                .orElseGet(() -> newBudget(campaign));
    }

    private CampaignBudget newBudget(Campaign campaign) {
        CampaignBudget budget = new CampaignBudget();
        budget.setTenantId(campaign.getTenantId());
        budget.setWorkspaceId(campaign.getWorkspaceId());
        budget.setCampaignId(campaign.getId());
        return budget;
    }

    private CampaignFrequencyPolicy getOrDefaultFrequency(Campaign campaign) {
        return frequencyPolicyRepository.findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(
                        campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId())
                .orElseGet(() -> newFrequencyPolicy(campaign));
    }

    private CampaignFrequencyPolicy newFrequencyPolicy(Campaign campaign) {
        CampaignFrequencyPolicy policy = new CampaignFrequencyPolicy();
        policy.setTenantId(campaign.getTenantId());
        policy.setWorkspaceId(campaign.getWorkspaceId());
        policy.setCampaignId(campaign.getId());
        Integer cap = campaign.getFrequencyCap();
        policy.setEnabled(cap != null && cap > 0);
        policy.setMaxSends(cap == null ? 0 : cap);
        return policy;
    }

    private CampaignEngineDto.ExperimentResponse toExperimentResponse(CampaignExperiment experiment) {
        return CampaignEngineDto.ExperimentResponse.builder()
                .id(experiment.getId())
                .campaignId(experiment.getCampaignId())
                .name(experiment.getName())
                .experimentType(experiment.getExperimentType())
                .status(experiment.getStatus())
                .winnerMetric(experiment.getWinnerMetric())
                .customMetricName(experiment.getCustomMetricName())
                .autoPromotion(experiment.isAutoPromotion())
                .minRecipientsPerVariant(experiment.getMinRecipientsPerVariant())
                .evaluationWindowHours(experiment.getEvaluationWindowHours())
                .holdoutPercentage(experiment.getHoldoutPercentage())
                .winnerVariantId(experiment.getWinnerVariantId())
                .factors(experiment.getFactors())
                .startsAt(experiment.getStartsAt())
                .endsAt(experiment.getEndsAt())
                .completedAt(experiment.getCompletedAt())
                .variants(variantRepository.findByTenantIdAndWorkspaceIdAndExperimentIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                                experiment.getTenantId(), experiment.getWorkspaceId(), experiment.getId())
                        .stream().map(this::toVariantResponse).toList())
                .createdAt(experiment.getCreatedAt())
                .updatedAt(experiment.getUpdatedAt())
                .build();
    }

    private CampaignEngineDto.VariantResponse toVariantResponse(CampaignVariant variant) {
        return CampaignEngineDto.VariantResponse.builder()
                .id(variant.getId())
                .experimentId(variant.getExperimentId())
                .variantKey(variant.getVariantKey())
                .name(variant.getName())
                .weight(variant.getWeight())
                .controlVariant(variant.isControlVariant())
                .holdoutVariant(variant.isHoldoutVariant())
                .active(variant.isActive())
                .winner(variant.isWinner())
                .contentId(variant.getContentId())
                .subjectOverride(variant.getSubjectOverride())
                .metadata(variant.getMetadata())
                .build();
    }

    private CampaignEngineDto.BudgetResponse toBudgetResponse(CampaignBudget budget) {
        return CampaignEngineDto.BudgetResponse.builder()
                .id(budget.getId())
                .campaignId(budget.getCampaignId())
                .currency(budget.getCurrency())
                .budgetLimit(budget.getBudgetLimit())
                .costPerSend(budget.getCostPerSend())
                .reservedSpend(budget.getReservedSpend())
                .actualSpend(budget.getActualSpend())
                .enforced(budget.isEnforced())
                .status(budget.getStatus())
                .build();
    }

    private CampaignEngineDto.FrequencyPolicyResponse toFrequencyResponse(CampaignFrequencyPolicy policy) {
        return CampaignEngineDto.FrequencyPolicyResponse.builder()
                .id(policy.getId())
                .campaignId(policy.getCampaignId())
                .enabled(policy.isEnabled())
                .maxSends(policy.getMaxSends())
                .windowHours(policy.getWindowHours())
                .includeJourneys(policy.isIncludeJourneys())
                .build();
    }

    private CampaignEngineDto.DeadLetterResponse toDeadLetterResponse(CampaignDeadLetter letter) {
        return CampaignEngineDto.DeadLetterResponse.builder()
                .id(letter.getId())
                .campaignId(letter.getCampaignId())
                .jobId(letter.getJobId())
                .batchId(letter.getBatchId())
                .subscriberId(letter.getSubscriberId())
                .email(letter.getEmail())
                .reason(letter.getReason())
                .payload(letter.getPayload())
                .retryCount(letter.getRetryCount())
                .status(letter.getStatus())
                .nextRetryAt(letter.getNextRetryAt())
                .replayedAt(letter.getReplayedAt())
                .createdAt(letter.getCreatedAt())
                .build();
    }
}

package com.legent.campaign.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.CampaignBudget;
import com.legent.campaign.domain.CampaignDeadLetter;
import com.legent.campaign.domain.CampaignExperiment;
import com.legent.campaign.domain.CampaignFrequencyPolicy;
import com.legent.campaign.domain.CampaignSendLedger;
import com.legent.campaign.domain.CampaignVariant;
import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.repository.CampaignBudgetRepository;
import com.legent.campaign.repository.CampaignDeadLetterRepository;
import com.legent.campaign.repository.CampaignExperimentRepository;
import com.legent.campaign.repository.CampaignFrequencyPolicyRepository;
import com.legent.campaign.repository.CampaignSendLedgerRepository;
import com.legent.campaign.repository.CampaignVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignSendSafetyService {

    private static final Collection<CampaignSendLedger.SendState> FREQUENCY_STATES = List.of(
            CampaignSendLedger.SendState.RESERVED,
            CampaignSendLedger.SendState.SENT
    );

    private final CampaignExperimentRepository experimentRepository;
    private final CampaignVariantRepository variantRepository;
    private final CampaignBudgetRepository budgetRepository;
    private final CampaignFrequencyPolicyRepository frequencyPolicyRepository;
    private final CampaignSendLedgerRepository ledgerRepository;
    private final CampaignDeadLetterRepository deadLetterRepository;
    private final DeterministicVariantAssignmentService assignmentService;
    private final CampaignMetricsService metricsService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SendPlan buildPlan(Campaign campaign) {
        Optional<CampaignExperiment> experiment = experimentRepository
                .findFirstByTenantIdAndWorkspaceIdAndCampaignIdAndStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
                        campaign.getTenantId(),
                        campaign.getWorkspaceId(),
                        campaign.getId(),
                        List.of(CampaignExperiment.ExperimentStatus.ACTIVE, CampaignExperiment.ExperimentStatus.PROMOTED));
        List<CampaignVariant> variants = experiment
                .map(value -> variantRepository
                        .findByTenantIdAndWorkspaceIdAndExperimentIdAndActiveTrueAndDeletedAtIsNullOrderByCreatedAtAsc(
                                campaign.getTenantId(), campaign.getWorkspaceId(), value.getId()))
                .orElse(List.of());
        CampaignBudget budget = budgetRepository
                .findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(
                        campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId())
                .orElse(null);
        CampaignFrequencyPolicy frequencyPolicy = frequencyPolicyRepository
                .findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(
                        campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId())
                .orElseGet(() -> defaultFrequency(campaign));
        return new SendPlan(experiment.orElse(null), variants, budget, frequencyPolicy);
    }

    @Transactional
    public PreparedRecipient prepareRecipient(Campaign campaign,
                                              SendBatch batch,
                                              SendPlan plan,
                                              Map<String, String> subscriber,
                                              String messageId) {
        String email = normalize(subscriber.get("email"));
        String subscriberId = normalize(subscriber.get("subscriberId"));
        String subscriberKey = subscriberId != null ? subscriberId : email;
        if (email == null) {
            return PreparedRecipient.skipped(messageId, null, null, null, null, "INVALID_EMAIL");
        }

        Optional<CampaignSendLedger> existing = ledgerRepository.findByTenantIdAndWorkspaceIdAndMessageIdAndDeletedAtIsNull(
                campaign.getTenantId(), campaign.getWorkspaceId(), messageId);
        if (existing.isPresent()) {
            CampaignSendLedger ledger = existing.get();
            if (ledger.getSendState() != CampaignSendLedger.SendState.FAILED) {
                return PreparedRecipient.skipped(messageId, ledger.getExperimentId(), ledger.getVariantId(), null, null, "DUPLICATE_SEND_GUARD");
            }
        }

        DeterministicVariantAssignmentService.Assignment assignment = assignmentService.assign(
                plan.experiment(), plan.variants(), subscriberKey);
        if (assignment.holdout()) {
            saveLedger(campaign, batch, subscriber, messageId, assignment, CampaignSendLedger.SendState.HOLDOUT, "HOLDOUT", BigDecimal.ZERO);
            metricsService.recordTarget(campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId(),
                    assignment.experimentId(), assignment.variantId(), true);
            return PreparedRecipient.skipped(messageId, assignment.experimentId(), assignment.variantId(), null, null, "HOLDOUT");
        }

        if (!passesFrequency(campaign, plan.frequencyPolicy(), email)) {
            saveLedger(campaign, batch, subscriber, messageId, assignment, CampaignSendLedger.SendState.SUPPRESSED, "FREQUENCY_CAP", BigDecimal.ZERO);
            return PreparedRecipient.skipped(messageId, assignment.experimentId(), assignment.variantId(), null, null, "FREQUENCY_CAP");
        }

        BigDecimal cost = plan.budget() == null || plan.budget().getCostPerSend() == null
                ? BigDecimal.ZERO : plan.budget().getCostPerSend();
        if (!reserveBudget(plan.budget(), cost)) {
            saveLedger(campaign, batch, subscriber, messageId, assignment, CampaignSendLedger.SendState.SUPPRESSED, "BUDGET_EXCEEDED", BigDecimal.ZERO);
            return PreparedRecipient.skipped(messageId, assignment.experimentId(), assignment.variantId(), null, null, "BUDGET_EXCEEDED");
        }

        saveLedger(campaign, batch, subscriber, messageId, assignment, CampaignSendLedger.SendState.RESERVED, null, cost);
        metricsService.recordTarget(campaign.getTenantId(), campaign.getWorkspaceId(), campaign.getId(),
                assignment.experimentId(), assignment.variantId(), false);
        return PreparedRecipient.send(
                subscriber,
                messageId,
                assignment.experimentId(),
                assignment.variantId(),
                assignment.contentId(),
                assignment.subjectOverride(),
                cost);
    }

    @Transactional
    public void recordDeliveryFeedback(String tenantId,
                                       String workspaceId,
                                       String messageId,
                                       boolean failed,
                                       String reason) {
        ledgerRepository.findByTenantIdAndWorkspaceIdAndMessageIdAndDeletedAtIsNull(tenantId, workspaceId, messageId)
                .ifPresent(ledger -> {
                    if (failed) {
                        ledger.setSendState(CampaignSendLedger.SendState.FAILED);
                        ledger.setReason(reason);
                        ledger.setFailedAt(Instant.now());
                    } else {
                        ledger.setSendState(CampaignSendLedger.SendState.SENT);
                        ledger.setCostActual(ledger.getCostReserved() == null ? BigDecimal.ZERO : ledger.getCostReserved());
                        ledger.setSentAt(Instant.now());
                    }
                    ledgerRepository.save(ledger);
                    settleBudget(ledger, failed);
                    metricsService.recordDelivery(tenantId, workspaceId, ledger.getCampaignId(),
                            ledger.getExperimentId(), ledger.getVariantId(), failed);
                });
    }

    @Transactional
    public void createDeadLetter(String tenantId,
                                 String workspaceId,
                                 String campaignId,
                                 String jobId,
                                 String batchId,
                                 String reason,
                                 Object payload,
                                 int retryCount) {
        CampaignDeadLetter letter = new CampaignDeadLetter();
        letter.setTenantId(tenantId);
        letter.setWorkspaceId(workspaceId);
        letter.setCampaignId(campaignId);
        letter.setJobId(jobId);
        letter.setBatchId(batchId);
        letter.setReason(reason);
        letter.setRetryCount(retryCount);
        try {
            letter.setPayload(objectMapper.writeValueAsString(payload == null ? Map.of() : payload));
        } catch (Exception e) {
            letter.setPayload("{}");
        }
        deadLetterRepository.save(letter);
    }

    private boolean passesFrequency(Campaign campaign, CampaignFrequencyPolicy policy, String email) {
        if (policy == null || !policy.isEnabled() || policy.getMaxSends() == null || policy.getMaxSends() <= 0) {
            return true;
        }
        int windowHours = policy.getWindowHours() == null || policy.getWindowHours() <= 0 ? 24 : policy.getWindowHours();
        long touches = ledgerRepository.countRecipientTouchesSince(
                campaign.getTenantId(),
                campaign.getWorkspaceId(),
                email,
                Instant.now().minusSeconds(windowHours * 3600L),
                FREQUENCY_STATES);
        return touches < policy.getMaxSends();
    }

    private boolean reserveBudget(CampaignBudget budget, BigDecimal cost) {
        if (budget == null || cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        BigDecimal reserved = budget.getReservedSpend() == null ? BigDecimal.ZERO : budget.getReservedSpend();
        BigDecimal actual = budget.getActualSpend() == null ? BigDecimal.ZERO : budget.getActualSpend();
        BigDecimal next = reserved.add(actual).add(cost);
        if (budget.isEnforced()
                && budget.getBudgetLimit() != null
                && budget.getBudgetLimit().compareTo(BigDecimal.ZERO) > 0
                && next.compareTo(budget.getBudgetLimit()) > 0) {
            budget.setStatus(CampaignBudget.STATUS_EXHAUSTED);
            budgetRepository.save(budget);
            return false;
        }
        budget.setReservedSpend(reserved.add(cost));
        budgetRepository.save(budget);
        return true;
    }

    private void settleBudget(CampaignSendLedger ledger, boolean failed) {
        budgetRepository.findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(
                        ledger.getTenantId(), ledger.getWorkspaceId(), ledger.getCampaignId())
                .ifPresent(budget -> {
                    BigDecimal reserved = budget.getReservedSpend() == null ? BigDecimal.ZERO : budget.getReservedSpend();
                    BigDecimal cost = ledger.getCostReserved() == null ? BigDecimal.ZERO : ledger.getCostReserved();
                    budget.setReservedSpend(reserved.subtract(cost).max(BigDecimal.ZERO));
                    if (!failed) {
                        BigDecimal actual = budget.getActualSpend() == null ? BigDecimal.ZERO : budget.getActualSpend();
                        budget.setActualSpend(actual.add(cost));
                    }
                    budgetRepository.save(budget);
                });
    }

    private CampaignSendLedger saveLedger(Campaign campaign,
                                          SendBatch batch,
                                          Map<String, String> subscriber,
                                          String messageId,
                                          DeterministicVariantAssignmentService.Assignment assignment,
                                          CampaignSendLedger.SendState state,
                                          String reason,
                                          BigDecimal cost) {
        CampaignSendLedger ledger = ledgerRepository.findByTenantIdAndWorkspaceIdAndMessageIdAndDeletedAtIsNull(
                        campaign.getTenantId(), campaign.getWorkspaceId(), messageId)
                .orElseGet(CampaignSendLedger::new);
        if (ledger.getTenantId() == null) {
            ledger.setTenantId(campaign.getTenantId());
        }
        ledger.setWorkspaceId(campaign.getWorkspaceId());
        ledger.setCampaignId(campaign.getId());
        ledger.setJobId(batch.getJobId());
        ledger.setBatchId(batch.getId());
        ledger.setMessageId(messageId);
        ledger.setSubscriberId(normalize(subscriber.get("subscriberId")));
        ledger.setEmail(normalize(subscriber.get("email")));
        ledger.setExperimentId(assignment.experimentId());
        ledger.setVariantId(assignment.variantId());
        ledger.setSendState(state);
        ledger.setReason(reason);
        ledger.setCostReserved(cost == null ? BigDecimal.ZERO : cost);
        return ledgerRepository.save(ledger);
    }

    private CampaignFrequencyPolicy defaultFrequency(Campaign campaign) {
        CampaignFrequencyPolicy policy = new CampaignFrequencyPolicy();
        policy.setTenantId(campaign.getTenantId());
        policy.setWorkspaceId(campaign.getWorkspaceId());
        policy.setCampaignId(campaign.getId());
        Integer cap = campaign.getFrequencyCap();
        policy.setEnabled(cap != null && cap > 0);
        policy.setMaxSends(cap == null ? 0 : cap);
        policy.setWindowHours(24);
        policy.setIncludeJourneys(true);
        return policy;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record SendPlan(CampaignExperiment experiment,
                           List<CampaignVariant> variants,
                           CampaignBudget budget,
                           CampaignFrequencyPolicy frequencyPolicy) {
    }

    public record PreparedRecipient(Map<String, String> subscriber,
                                    String messageId,
                                    String experimentId,
                                    String variantId,
                                    String contentIdOverride,
                                    String subjectOverride,
                                    BigDecimal costReserved,
                                    boolean send,
                                    String skipReason) {
        static PreparedRecipient send(Map<String, String> subscriber,
                                      String messageId,
                                      String experimentId,
                                      String variantId,
                                      String contentIdOverride,
                                      String subjectOverride,
                                      BigDecimal costReserved) {
            return new PreparedRecipient(subscriber, messageId, experimentId, variantId,
                    contentIdOverride, subjectOverride, costReserved, true, null);
        }

        static PreparedRecipient skipped(String messageId,
                                         String experimentId,
                                         String variantId,
                                         String contentIdOverride,
                                         String subjectOverride,
                                         String reason) {
            return new PreparedRecipient(Map.of(), messageId, experimentId, variantId,
                    contentIdOverride, subjectOverride, BigDecimal.ZERO, false, reason);
        }
    }
}

package com.legent.campaign.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.CampaignBudget;
import com.legent.campaign.domain.CampaignFrequencyPolicy;
import com.legent.campaign.domain.CampaignSendLedger;
import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.repository.CampaignBudgetRepository;
import com.legent.campaign.repository.CampaignDeadLetterRepository;
import com.legent.campaign.repository.CampaignExperimentRepository;
import com.legent.campaign.repository.CampaignFrequencyPolicyRepository;
import com.legent.campaign.repository.CampaignSendLedgerRepository;
import com.legent.campaign.repository.CampaignVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignSendSafetyServiceTest {

    @Mock private CampaignExperimentRepository experimentRepository;
    @Mock private CampaignVariantRepository variantRepository;
    @Mock private CampaignBudgetRepository budgetRepository;
    @Mock private CampaignFrequencyPolicyRepository frequencyPolicyRepository;
    @Mock private CampaignSendLedgerRepository ledgerRepository;
    @Mock private CampaignDeadLetterRepository deadLetterRepository;
    @Mock private CampaignMetricsService metricsService;

    private CampaignSendSafetyService safetyService;

    @BeforeEach
    void setUp() {
        safetyService = new CampaignSendSafetyService(
                experimentRepository,
                variantRepository,
                budgetRepository,
                frequencyPolicyRepository,
                ledgerRepository,
                deadLetterRepository,
                new DeterministicVariantAssignmentService(),
                metricsService,
                new ObjectMapper());
    }

    @Test
    void blocksDuplicateMessageBeforeSecondSend() {
        CampaignSendLedger existing = new CampaignSendLedger();
        existing.setExperimentId("exp-1");
        existing.setVariantId("var-a");
        existing.setSendState(CampaignSendLedger.SendState.SENT);
        when(ledgerRepository.findByTenantIdAndWorkspaceIdAndMessageIdAndDeletedAtIsNull(
                "tenant-1", "workspace-1", "msg-1")).thenReturn(Optional.of(existing));

        var prepared = safetyService.prepareRecipient(
                campaign(),
                batch(),
                new CampaignSendSafetyService.SendPlan(null, List.of(), null, disabledPolicy()),
                Map.of("email", "person@example.com", "subscriberId", "sub-1"),
                "msg-1");

        assertThat(prepared.send()).isFalse();
        assertThat(prepared.skipReason()).isEqualTo("DUPLICATE_SEND_GUARD");
        assertThat(prepared.experimentId()).isEqualTo("exp-1");
    }

    @Test
    void reservesBudgetAndCreatesLedgerForEligibleRecipient() {
        CampaignBudget budget = budget(new BigDecimal("10.00"), new BigDecimal("0.01"), true);
        when(ledgerRepository.findByTenantIdAndWorkspaceIdAndMessageIdAndDeletedAtIsNull(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(ledgerRepository.save(any(CampaignSendLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var prepared = safetyService.prepareRecipient(
                campaign(),
                batch(),
                new CampaignSendSafetyService.SendPlan(null, List.of(), budget, disabledPolicy()),
                Map.of("email", "person@example.com", "subscriberId", "sub-1"),
                "msg-1");

        assertThat(prepared.send()).isTrue();
        assertThat(prepared.costReserved()).isEqualByComparingTo("0.01");
        assertThat(budget.getReservedSpend()).isEqualByComparingTo("0.01");
        verify(budgetRepository).save(budget);
        verify(ledgerRepository).save(any(CampaignSendLedger.class));
    }

    @Test
    void blocksRecipientWhenEnforcedBudgetWouldBeExceeded() {
        CampaignBudget budget = budget(new BigDecimal("0.01"), new BigDecimal("0.02"), true);
        when(ledgerRepository.findByTenantIdAndWorkspaceIdAndMessageIdAndDeletedAtIsNull(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(ledgerRepository.save(any(CampaignSendLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var prepared = safetyService.prepareRecipient(
                campaign(),
                batch(),
                new CampaignSendSafetyService.SendPlan(null, List.of(), budget, disabledPolicy()),
                Map.of("email", "person@example.com", "subscriberId", "sub-1"),
                "msg-1");

        assertThat(prepared.send()).isFalse();
        assertThat(prepared.skipReason()).isEqualTo("BUDGET_EXCEEDED");
        assertThat(budget.getStatus()).isEqualTo(CampaignBudget.STATUS_EXHAUSTED);
        verify(budgetRepository).save(budget);
    }

    private Campaign campaign() {
        Campaign campaign = new Campaign();
        campaign.setId("campaign-1");
        campaign.setTenantId("tenant-1");
        campaign.setWorkspaceId("workspace-1");
        campaign.setName("Campaign");
        return campaign;
    }

    private SendBatch batch() {
        SendBatch batch = new SendBatch();
        batch.setId("batch-1");
        batch.setTenantId("tenant-1");
        batch.setWorkspaceId("workspace-1");
        batch.setCampaignId("campaign-1");
        batch.setJobId("job-1");
        return batch;
    }

    private CampaignFrequencyPolicy disabledPolicy() {
        CampaignFrequencyPolicy policy = new CampaignFrequencyPolicy();
        policy.setEnabled(false);
        policy.setMaxSends(0);
        policy.setWindowHours(24);
        return policy;
    }

    private CampaignBudget budget(BigDecimal limit, BigDecimal cost, boolean enforced) {
        CampaignBudget budget = new CampaignBudget();
        budget.setTenantId("tenant-1");
        budget.setWorkspaceId("workspace-1");
        budget.setCampaignId("campaign-1");
        budget.setBudgetLimit(limit);
        budget.setCostPerSend(cost);
        budget.setReservedSpend(BigDecimal.ZERO);
        budget.setActualSpend(BigDecimal.ZERO);
        budget.setEnforced(enforced);
        return budget;
    }
}

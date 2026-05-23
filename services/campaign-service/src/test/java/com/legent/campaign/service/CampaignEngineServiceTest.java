package com.legent.campaign.service;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.CampaignDeadLetter;
import com.legent.campaign.domain.CampaignExperiment;
import com.legent.campaign.dto.CampaignEngineDto;
import com.legent.campaign.event.CampaignEventPublisher;
import com.legent.campaign.repository.CampaignBudgetRepository;
import com.legent.campaign.repository.CampaignDeadLetterRepository;
import com.legent.campaign.repository.CampaignExperimentRepository;
import com.legent.campaign.repository.CampaignFrequencyPolicyRepository;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.CampaignSendLedgerRepository;
import com.legent.campaign.repository.CampaignVariantRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignEngineServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignExperimentRepository experimentRepository;
    @Mock private CampaignVariantRepository variantRepository;
    @Mock private CampaignBudgetRepository budgetRepository;
    @Mock private CampaignFrequencyPolicyRepository frequencyPolicyRepository;
    @Mock private CampaignSendLedgerRepository ledgerRepository;
    @Mock private CampaignDeadLetterRepository deadLetterRepository;
    @Mock private CampaignMetricsService metricsService;
    @Mock private CampaignEventPublisher eventPublisher;
    @Mock private CampaignLaunchReadinessGate launchReadinessGate;

    @InjectMocks private CampaignEngineService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        lenient().when(budgetRepository.findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(
                anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(frequencyPolicyRepository.findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(
                anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(experimentRepository.findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                anyString(), anyString(), anyString())).thenReturn(List.of());
        lenient().when(launchReadinessGate.evaluate(org.mockito.ArgumentMatchers.any(Campaign.class)))
                .thenReturn(CampaignLaunchReadinessGate.GateResult.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void preflightAllowsAlignedSenderAndSendingDomain() {
        Campaign campaign = completeCampaign();
        campaign.setSenderEmail("Marketing@Example.COM");
        campaign.setSendingDomain("example.com.");
        mockCampaign(campaign);

        CampaignEngineDto.SendPreflightReport report = service.preflight("campaign-1");

        assertThat(report.isSendAllowed()).isTrue();
        assertThat(report.getErrors()).isEmpty();
        assertThat(report.getChecks()).containsEntry("senderDomain", "example.com");
        assertThat(report.getChecks()).containsEntry("sendingDomain", "example.com");
    }

    @Test
    void preflightBlocksMissingSendingDomain() {
        Campaign campaign = completeCampaign();
        campaign.setSendingDomain(null);
        mockCampaign(campaign);

        CampaignEngineDto.SendPreflightReport report = service.preflight("campaign-1");

        assertThat(report.isSendAllowed()).isFalse();
        assertThat(report.getErrors()).contains("Sending domain is required before send.");
    }

    @Test
    void preflightBlocksSenderDomainMismatch() {
        Campaign campaign = completeCampaign();
        campaign.setSenderEmail("marketing@example.com");
        campaign.setSendingDomain("other.example");
        mockCampaign(campaign);

        CampaignEngineDto.SendPreflightReport report = service.preflight("campaign-1");

        assertThat(report.isSendAllowed()).isFalse();
        assertThat(report.getErrors()).contains("Sender email domain must match the selected sending domain before send.");
    }

    @Test
    void preflightBlocksSenderEmailWithoutDomain() {
        Campaign campaign = completeCampaign();
        campaign.setSenderEmail("marketing");
        mockCampaign(campaign);

        CampaignEngineDto.SendPreflightReport report = service.preflight("campaign-1");

        assertThat(report.isSendAllowed()).isFalse();
        assertThat(report.getErrors()).contains("Sender email must include a valid domain before send.");
    }

    @Test
    void listDeadLettersUsesDefaultBound() {
        CampaignDeadLetter letter = deadLetter("dead-letter-1");
        when(deadLetterRepository.findByTenantIdAndWorkspaceIdAndJobIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq("tenant-1"), eq("workspace-1"), eq("job-1"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(letter));

        List<CampaignEngineDto.DeadLetterResponse> responses = service.listDeadLetters("job-1");

        assertThat(responses).extracting(CampaignEngineDto.DeadLetterResponse::getId)
                .containsExactly("dead-letter-1");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(deadLetterRepository).findByTenantIdAndWorkspaceIdAndJobIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq("tenant-1"), eq("workspace-1"), eq("job-1"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(CampaignEngineService.DEFAULT_DEAD_LETTER_LIMIT);
    }

    @Test
    void listDeadLettersClampsLimitToMaxBound() {
        when(deadLetterRepository.findByTenantIdAndWorkspaceIdAndJobIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq("tenant-1"), eq("workspace-1"), eq("job-1"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of());

        service.listDeadLetters("job-1", CampaignEngineService.MAX_DEAD_LETTER_LIMIT + 1_000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(deadLetterRepository).findByTenantIdAndWorkspaceIdAndJobIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq("tenant-1"), eq("workspace-1"), eq("job-1"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(CampaignEngineService.MAX_DEAD_LETTER_LIMIT);
    }

    @Test
    void evaluateExperimentWinnersUsesBoundedDeterministicFirstScan() {
        CampaignExperiment experiment = activeExperiment("experiment-1", true);
        when(experimentRepository.findByStatusAndDeletedAtIsNullOrderByIdAsc(
                eq(CampaignExperiment.ExperimentStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(experimentSlice(List.of(experiment), false));
        when(metricsService.chooseWinner(
                eq("tenant-1"), eq("workspace-1"), eq("campaign-1"), eq(experiment)))
                .thenReturn(Optional.of(winner("variant-1")));

        service.evaluateExperimentWinners();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(experimentRepository).findByStatusAndDeletedAtIsNullOrderByIdAsc(
                eq(CampaignExperiment.ExperimentStatus.ACTIVE), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize())
                .isEqualTo(CampaignEngineService.EXPERIMENT_WINNER_EVALUATION_PAGE_SIZE);
        verify(experimentRepository, never()).findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
                eq(CampaignExperiment.ExperimentStatus.ACTIVE), anyString(), any(Pageable.class));
        verify(experimentRepository).save(experiment);
        assertThat(experiment.getWinnerVariantId()).isEqualTo("variant-1");
        assertThat(experiment.getStatus()).isEqualTo(CampaignExperiment.ExperimentStatus.PROMOTED);
    }

    @Test
    void evaluateExperimentWinnersProcessesMultiplePagesWithoutEvaluatingManualPromotionExperiments() {
        CampaignExperiment manualExperiment = activeExperiment("experiment-1", false);
        CampaignExperiment autoExperiment = activeExperiment("experiment-2", true);
        when(experimentRepository.findByStatusAndDeletedAtIsNullOrderByIdAsc(
                eq(CampaignExperiment.ExperimentStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(experimentSlice(List.of(manualExperiment), true));
        when(experimentRepository.findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
                eq(CampaignExperiment.ExperimentStatus.ACTIVE), eq("experiment-1"), any(Pageable.class)))
                .thenReturn(experimentSlice(List.of(autoExperiment), false));
        when(metricsService.chooseWinner(
                eq("tenant-1"), eq("workspace-1"), eq("campaign-1"), eq(autoExperiment)))
                .thenReturn(Optional.of(winner("variant-2")));

        service.evaluateExperimentWinners();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(experimentRepository).findByStatusAndDeletedAtIsNullOrderByIdAsc(
                eq(CampaignExperiment.ExperimentStatus.ACTIVE), pageable.capture());
        verify(experimentRepository).findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
                eq(CampaignExperiment.ExperimentStatus.ACTIVE), eq("experiment-1"), pageable.capture());
        assertThat(pageable.getAllValues())
                .extracting(Pageable::getPageNumber)
                .containsExactly(0, 0);
        assertThat(pageable.getAllValues())
                .extracting(Pageable::getPageSize)
                .containsExactly(
                        CampaignEngineService.EXPERIMENT_WINNER_EVALUATION_PAGE_SIZE,
                        CampaignEngineService.EXPERIMENT_WINNER_EVALUATION_PAGE_SIZE);
        verify(metricsService, never()).chooseWinner(
                eq("tenant-1"), eq("workspace-1"), eq("campaign-1"), eq(manualExperiment));
        verify(experimentRepository).save(autoExperiment);
        assertThat(autoExperiment.getWinnerVariantId()).isEqualTo("variant-2");
        assertThat(autoExperiment.getStatus()).isEqualTo(CampaignExperiment.ExperimentStatus.PROMOTED);
    }

    private void mockCampaign(Campaign campaign) {
        when(campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                "tenant-1", "workspace-1", "campaign-1")).thenReturn(Optional.of(campaign));
    }

    private Campaign completeCampaign() {
        Campaign campaign = new Campaign();
        campaign.setId("campaign-1");
        campaign.setTenantId("tenant-1");
        campaign.setWorkspaceId("workspace-1");
        campaign.setName("Ready campaign");
        campaign.setSubject("Launch subject");
        campaign.setContentId("template-1");
        campaign.setSenderEmail("marketing@example.com");
        campaign.setSendingDomain("example.com");
        campaign.setApprovalRequired(false);
        campaign.addAudience("LIST", "list-1");
        return campaign;
    }

    private CampaignExperiment activeExperiment(String id, boolean autoPromotion) {
        CampaignExperiment experiment = new CampaignExperiment();
        experiment.setId(id);
        experiment.setTenantId("tenant-1");
        experiment.setWorkspaceId("workspace-1");
        experiment.setCampaignId("campaign-1");
        experiment.setName("Experiment " + id);
        experiment.setStatus(CampaignExperiment.ExperimentStatus.ACTIVE);
        experiment.setAutoPromotion(autoPromotion);
        return experiment;
    }

    private Slice<CampaignExperiment> experimentSlice(List<CampaignExperiment> experiments, boolean hasNext) {
        return new SliceImpl<>(
                experiments,
                PageRequest.of(0, CampaignEngineService.EXPERIMENT_WINNER_EVALUATION_PAGE_SIZE),
                hasNext);
    }

    private CampaignEngineDto.VariantMetricsResponse winner(String variantId) {
        return CampaignEngineDto.VariantMetricsResponse.builder()
                .campaignId("campaign-1")
                .experimentId("experiment-1")
                .variantId(variantId)
                .build();
    }

    private CampaignDeadLetter deadLetter(String id) {
        CampaignDeadLetter letter = new CampaignDeadLetter();
        letter.setId(id);
        letter.setTenantId("tenant-1");
        letter.setWorkspaceId("workspace-1");
        letter.setCampaignId("campaign-1");
        letter.setJobId("job-1");
        letter.setBatchId("batch-1");
        letter.setReason("Permanent failure");
        return letter;
    }
}

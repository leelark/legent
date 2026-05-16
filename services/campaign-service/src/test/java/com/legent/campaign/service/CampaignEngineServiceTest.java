package com.legent.campaign.service;

import com.legent.campaign.domain.Campaign;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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
}

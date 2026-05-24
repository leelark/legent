package com.legent.campaign.service;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.dto.CampaignDto;
import com.legent.campaign.mapper.CampaignMapper;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.common.exception.ValidationException;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignServiceScheduleTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignMapper campaignMapper;
    @Mock private CampaignStateMachineService stateMachine;
    @Mock private CampaignLockService campaignLockService;

    private CampaignService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        service = new CampaignService(campaignRepository, campaignMapper, stateMachine, campaignLockService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void schedulePersistsApprovedSendTimeOptimizationEvidence() {
        Campaign campaign = approvedCampaign();
        Instant originalAt = Instant.now().plusSeconds(1_800);
        Instant recommendedAt = Instant.now().plusSeconds(3_600);
        when(campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                "tenant-1", "workspace-1", "campaign-1")).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(campaignMapper.toResponse(any(Campaign.class))).thenReturn(CampaignDto.Response.builder()
                .id("campaign-1")
                .scheduledAt(recommendedAt)
                .build());

        CampaignDto.ScheduleRequest request = CampaignDto.ScheduleRequest.builder()
                .scheduledAt(recommendedAt)
                .sendTimeOptimization(validDecision(originalAt, recommendedAt))
                .build();

        service.schedule("campaign-1", request);

        ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
        verify(campaignRepository).save(captor.capture());
        Campaign saved = captor.getValue();
        assertThat(saved.getScheduledAt()).isEqualTo(recommendedAt);
        assertThat(saved.getSendTimeOptimizationType()).isEqualTo("SEND_TIME");
        assertThat(saved.getSendTimeOptimizationRunId()).isEqualTo("sto-run-1");
        assertThat(saved.getSendTimeOptimizationSnapshotHash()).isEqualTo("snapshot-1");
        assertThat(saved.isSendTimeOptimizationApproved()).isTrue();
        assertThat(saved.isSendTimeOptimizationProviderCapacityGatePassed()).isTrue();
        verify(campaignLockService).lockCampaign(saved);
    }

    @Test
    void scheduleRejectsLowConfidenceSendTimeOptimizationEvidence() {
        Campaign campaign = approvedCampaign();
        Instant originalAt = Instant.now().plusSeconds(1_800);
        Instant recommendedAt = Instant.now().plusSeconds(3_600);
        when(campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                "tenant-1", "workspace-1", "campaign-1")).thenReturn(Optional.of(campaign));
        CampaignDto.SendTimeOptimizationDecision decision = validDecision(originalAt, recommendedAt);
        decision.setConfidenceBand("LOW");

        assertThatThrownBy(() -> service.schedule("campaign-1", CampaignDto.ScheduleRequest.builder()
                .scheduledAt(recommendedAt)
                .sendTimeOptimization(decision)
                .build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("medium or high confidence");

        verify(campaignRepository, never()).save(any());
        verify(campaignLockService, never()).lockCampaign(any());
    }

    @Test
    void scheduleUsesApprovedSendTimeOptimizationRecommendationWhenScheduledAtIsAbsent() {
        Campaign campaign = approvedCampaign();
        Instant originalAt = Instant.now().plusSeconds(1_800);
        Instant recommendedAt = Instant.now().plusSeconds(3_600);
        when(campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                "tenant-1", "workspace-1", "campaign-1")).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(campaignMapper.toResponse(any(Campaign.class))).thenReturn(CampaignDto.Response.builder()
                .id("campaign-1")
                .scheduledAt(recommendedAt)
                .build());

        service.schedule("campaign-1", CampaignDto.ScheduleRequest.builder()
                .sendTimeOptimization(validDecision(originalAt, recommendedAt))
                .build());

        assertThat(campaign.getScheduledAt()).isEqualTo(recommendedAt);
        verify(campaignLockService).lockCampaign(campaign);
    }

    private Campaign approvedCampaign() {
        Campaign campaign = new Campaign();
        campaign.setId("campaign-1");
        campaign.setTenantId("tenant-1");
        campaign.setWorkspaceId("workspace-1");
        campaign.setName("Ready campaign");
        campaign.setTimezone("UTC");
        campaign.setApprovalRequired(true);
        campaign.setStatus(Campaign.CampaignStatus.APPROVED);
        return campaign;
    }

    private CampaignDto.SendTimeOptimizationDecision validDecision(Instant originalAt, Instant recommendedAt) {
        return CampaignDto.SendTimeOptimizationDecision.builder()
                .optimizationType("SEND_TIME")
                .policyKey("sto-commercial")
                .optimizationRunId("sto-run-1")
                .snapshotHash("snapshot-1")
                .originalScheduledAt(originalAt)
                .recommendedScheduledAt(recommendedAt)
                .timezone("UTC")
                .confidenceBand("HIGH")
                .fallbackMode("NONE")
                .blockedReasons(List.of())
                .dataQualityReasons(List.of("coverage:ok"))
                .reasonCodes(List.of("BEST_ENGAGEMENT_WINDOW"))
                .approvalRequired(true)
                .rollbackRequired(true)
                .approved(true)
                .approvalId("approval-1")
                .approvedBy("user-1")
                .approvedAt(Instant.now())
                .rollbackSnapshotId("rollback-1")
                .quietHoursGatePassed(true)
                .approvalGatePassed(true)
                .suppressionGatePassed(true)
                .warmupGatePassed(true)
                .rateLimitGatePassed(true)
                .providerCapacityGatePassed(true)
                .deliverabilityGatePassed(true)
                .build();
    }
}

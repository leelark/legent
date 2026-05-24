package com.legent.campaign.service;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.CampaignAudience;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.event.CampaignEventPublisher;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendJobRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulingServiceTest {

    @Mock private SendJobRepository sendJobRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignEventPublisher eventPublisher;
    @Mock private CampaignStateMachineService stateMachine;
    @Mock private CampaignLockService campaignLockService;

    private SchedulingService service;

    @BeforeEach
    void setUp() {
        service = new SchedulingService(
                sendJobRepository,
                campaignRepository,
                eventPublisher,
                stateMachine,
                campaignLockService);
        ReflectionTestUtils.setField(service, "dueJobPageSize", 2);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void processScheduledJobsUsesBoundedPageAndClaimsBeforePublishing() {
        SendJob job = dueJob();
        Campaign campaign = campaign();
        when(sendJobRepository.findByStatusAndScheduledAtBeforeAndDeletedAtIsNullOrderByScheduledAtAscCreatedAtAsc(
                eq(SendJob.JobStatus.PENDING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(job));
        when(sendJobRepository.claimDueScheduledJob(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("job-1"),
                eq(SendJob.JobStatus.PENDING),
                eq(SendJob.JobStatus.RESOLVING),
                any(Instant.class),
                any(Instant.class)))
                .thenReturn(1);
        when(campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                "tenant-1", "workspace-1", "campaign-1")).thenReturn(Optional.of(campaign));
        doAnswer(invocation -> {
            assertEquals("tenant-1", TenantContext.requireTenantId());
            assertEquals("workspace-1", TenantContext.requireWorkspaceId());
            return null;
        }).when(eventPublisher).publishAudienceResolutionRequested(
                eq("tenant-1"),
                eq("campaign-1"),
                eq("job-1"),
                any());

        service.processScheduledJobs();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(sendJobRepository).findByStatusAndScheduledAtBeforeAndDeletedAtIsNullOrderByScheduledAtAscCreatedAtAsc(
                eq(SendJob.JobStatus.PENDING), any(Instant.class), pageable.capture());
        assertEquals(2, pageable.getValue().getPageSize());
        verify(eventPublisher).publishAudienceResolutionRequested(
                eq("tenant-1"),
                eq("campaign-1"),
                eq("job-1"),
                any());
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
    }

    @Test
    void processScheduledJobsSkipsWhenAtomicClaimLosesRace() {
        SendJob job = dueJob();
        when(sendJobRepository.findByStatusAndScheduledAtBeforeAndDeletedAtIsNullOrderByScheduledAtAscCreatedAtAsc(
                eq(SendJob.JobStatus.PENDING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(job));
        when(sendJobRepository.claimDueScheduledJob(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("job-1"),
                eq(SendJob.JobStatus.PENDING),
                eq(SendJob.JobStatus.RESOLVING),
                any(Instant.class),
                any(Instant.class)))
                .thenReturn(0);

        service.processScheduledJobs();

        verify(campaignRepository, never()).findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(anyString(), anyString(), anyString());
        verify(eventPublisher, never()).publishAudienceResolutionRequested(anyString(), anyString(), anyString(), any());
    }

    @Test
    void processScheduledJobsPublishesWhenStoredSendTimeOptimizationMatchesJob() {
        SendJob job = dueJob();
        Campaign campaign = campaign();
        applyStoredSendTimeOptimization(campaign, job.getScheduledAt());
        when(sendJobRepository.findByStatusAndScheduledAtBeforeAndDeletedAtIsNullOrderByScheduledAtAscCreatedAtAsc(
                eq(SendJob.JobStatus.PENDING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(job));
        when(sendJobRepository.claimDueScheduledJob(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("job-1"),
                eq(SendJob.JobStatus.PENDING),
                eq(SendJob.JobStatus.RESOLVING),
                any(Instant.class),
                any(Instant.class)))
                .thenReturn(1);
        when(campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                "tenant-1", "workspace-1", "campaign-1")).thenReturn(Optional.of(campaign));

        service.processScheduledJobs();

        verify(eventPublisher).publishAudienceResolutionRequested(
                eq("tenant-1"),
                eq("campaign-1"),
                eq("job-1"),
                any());
    }

    @Test
    void processScheduledJobsFailsWhenStoredSendTimeOptimizationMismatchesJobSchedule() {
        SendJob job = dueJob();
        Campaign campaign = campaign();
        applyStoredSendTimeOptimization(campaign, job.getScheduledAt().plusSeconds(300));
        when(sendJobRepository.findByStatusAndScheduledAtBeforeAndDeletedAtIsNullOrderByScheduledAtAscCreatedAtAsc(
                eq(SendJob.JobStatus.PENDING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(job));
        when(sendJobRepository.claimDueScheduledJob(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("job-1"),
                eq(SendJob.JobStatus.PENDING),
                eq(SendJob.JobStatus.RESOLVING),
                any(Instant.class),
                any(Instant.class)))
                .thenReturn(1);
        when(campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                "tenant-1", "workspace-1", "campaign-1")).thenReturn(Optional.of(campaign));

        service.processScheduledJobs();

        verify(eventPublisher, never()).publishAudienceResolutionRequested(anyString(), anyString(), anyString(), any());
        verify(sendJobRepository).save(job);
    }

    private SendJob dueJob() {
        SendJob job = new SendJob();
        job.setId("job-1");
        job.setTenantId("tenant-1");
        job.setWorkspaceId("workspace-1");
        job.setCampaignId("campaign-1");
        job.setStatus(SendJob.JobStatus.PENDING);
        job.setScheduledAt(Instant.now().minusSeconds(60));
        return job;
    }

    private Campaign campaign() {
        Campaign campaign = new Campaign();
        campaign.setId("campaign-1");
        campaign.setTenantId("tenant-1");
        campaign.setWorkspaceId("workspace-1");
        campaign.setTimezone("UTC");
        campaign.setStatus(Campaign.CampaignStatus.APPROVED);
        campaign.addAudience(CampaignAudience.AudienceType.LIST, "list-1", CampaignAudience.AudienceAction.INCLUDE);
        return campaign;
    }

    private void applyStoredSendTimeOptimization(Campaign campaign, Instant recommendedAt) {
        campaign.setSendTimeOptimizationType("SEND_TIME");
        campaign.setSendTimeOptimizationPolicyKey("sto-commercial");
        campaign.setSendTimeOptimizationRunId("sto-run-1");
        campaign.setSendTimeOptimizationSnapshotHash("snapshot-1");
        campaign.setSendTimeOptimizationOriginalScheduledAt(recommendedAt.minusSeconds(600));
        campaign.setSendTimeOptimizationRecommendedScheduledAt(recommendedAt);
        campaign.setSendTimeOptimizationTimezone("UTC");
        campaign.setSendTimeOptimizationConfidenceBand("HIGH");
        campaign.setSendTimeOptimizationFallbackMode("NONE");
        campaign.setSendTimeOptimizationBlockedReasons(List.of());
        campaign.setSendTimeOptimizationDataQualityReasons(List.of("coverage:ok"));
        campaign.setSendTimeOptimizationReasonCodes(List.of("BEST_ENGAGEMENT_WINDOW"));
        campaign.setSendTimeOptimizationApprovalRequired(true);
        campaign.setSendTimeOptimizationRollbackRequired(true);
        campaign.setSendTimeOptimizationApproved(true);
        campaign.setSendTimeOptimizationApprovalId("approval-1");
        campaign.setSendTimeOptimizationApprovedBy("user-1");
        campaign.setSendTimeOptimizationApprovedAt(Instant.now().minusSeconds(900));
        campaign.setSendTimeOptimizationRollbackSnapshotId("rollback-1");
        campaign.setSendTimeOptimizationQuietHoursGatePassed(true);
        campaign.setSendTimeOptimizationApprovalGatePassed(true);
        campaign.setSendTimeOptimizationSuppressionGatePassed(true);
        campaign.setSendTimeOptimizationWarmupGatePassed(true);
        campaign.setSendTimeOptimizationRateLimitGatePassed(true);
        campaign.setSendTimeOptimizationProviderCapacityGatePassed(true);
        campaign.setSendTimeOptimizationDeliverabilityGatePassed(true);
    }
}

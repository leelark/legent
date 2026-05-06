package com.legent.campaign.service;

import java.time.Instant;
import java.util.Optional;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.dto.SendJobDto;
import com.legent.campaign.event.CampaignEventPublisher;
import com.legent.campaign.mapper.SendJobMapper;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendBatchRepository;
import com.legent.campaign.repository.SendJobRepository;
import com.legent.common.exception.ValidationException;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrchestrationService Unit Tests")
class OrchestrationServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private SendBatchRepository sendBatchRepository;
    @Mock private SendJobRepository sendJobRepository;
    @Mock private CampaignEventPublisher eventPublisher;
    @Mock private SendJobMapper sendJobMapper;
    @Mock private CampaignStateMachineService stateMachine;

    @InjectMocks private OrchestrationService orchestrationService;

    private static final String TENANT_ID = "tenant-test";
    private static final String WORKSPACE_ID = "workspace-test";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        TenantContext.setWorkspaceId(WORKSPACE_ID);
        lenient().doAnswer(invocation -> {
            Campaign campaign = invocation.getArgument(0);
            Campaign.CampaignStatus next = invocation.getArgument(1);
            campaign.setStatus(next);
            return null;
        }).when(stateMachine).transitionCampaign(any(Campaign.class), any(Campaign.CampaignStatus.class), anyString());
        lenient().doAnswer(invocation -> {
            SendJob job = invocation.getArgument(0);
            SendJob.JobStatus next = invocation.getArgument(1);
            job.setStatus(next);
            return null;
        }).when(stateMachine).transitionJob(any(SendJob.class), any(SendJob.JobStatus.class), anyString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void triggerSend_Immediate_Success() {
        String campaignId = "camp-1";
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setTenantId(TENANT_ID);
        campaign.setWorkspaceId(WORKSPACE_ID);
        campaign.setStatus(Campaign.CampaignStatus.APPROVED);
        campaign.addAudience("LIST", "audience-1");

        SendJobDto.TriggerRequest request = new SendJobDto.TriggerRequest();

        SendJob savedJob = new SendJob();
        savedJob.setId("job-1");
        savedJob.setCampaignId(campaignId);
        savedJob.setStatus(SendJob.JobStatus.RESOLVING);

        SendJobDto.Response expectedResponse = new SendJobDto.Response();
        expectedResponse.setId("job-1");
        expectedResponse.setStatus(SendJob.JobStatus.RESOLVING);

        when(campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, campaignId))
                .thenReturn(Optional.of(campaign));
        when(sendJobRepository.save(any(SendJob.class))).thenReturn(savedJob);
        when(sendJobMapper.toJobResponse(savedJob)).thenReturn(expectedResponse);

        SendJobDto.Response response = orchestrationService.triggerSend(campaignId, request);

        assertThat(response.getStatus()).isEqualTo(SendJob.JobStatus.RESOLVING);
        assertThat(campaign.getStatus()).isEqualTo(Campaign.CampaignStatus.SENDING);
        verify(campaignRepository).save(campaign);
        verify(eventPublisher).publishAudienceResolutionRequested(eq(TENANT_ID), eq(campaignId), eq("job-1"), anyList());
    }

    @Test
    void triggerSend_Scheduled_Success() {
        String campaignId = "camp-1";
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setTenantId(TENANT_ID);
        campaign.setWorkspaceId(WORKSPACE_ID);
        campaign.setStatus(Campaign.CampaignStatus.APPROVED);
        campaign.addAudience("LIST", "audience-1");

        SendJobDto.TriggerRequest request = new SendJobDto.TriggerRequest();
        request.setScheduledAt(Instant.now().plusSeconds(3600));

        SendJob savedJob = new SendJob();
        savedJob.setId("job-1");
        savedJob.setCampaignId(campaignId);
        savedJob.setStatus(SendJob.JobStatus.PENDING);
        savedJob.setScheduledAt(request.getScheduledAt());

        SendJobDto.Response expectedResponse = new SendJobDto.Response();
        expectedResponse.setId("job-1");
        expectedResponse.setStatus(SendJob.JobStatus.PENDING);

        when(campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, campaignId))
                .thenReturn(Optional.of(campaign));
        when(sendJobRepository.save(any(SendJob.class))).thenReturn(savedJob);
        when(sendJobMapper.toJobResponse(savedJob)).thenReturn(expectedResponse);

        SendJobDto.Response response = orchestrationService.triggerSend(campaignId, request);

        assertThat(response.getStatus()).isEqualTo(SendJob.JobStatus.PENDING);
        assertThat(campaign.getStatus()).isEqualTo(Campaign.CampaignStatus.SCHEDULED);
        verify(eventPublisher, never()).publishAudienceResolutionRequested(eq(TENANT_ID), eq(campaignId), eq("job-1"), anyList());
    }

    @Test
    void triggerSend_InvalidStatus_ThrowsException() {
        String campaignId = "camp-1";
        Campaign campaign = new Campaign();
        campaign.setStatus(Campaign.CampaignStatus.SENDING);
        campaign.setTenantId(TENANT_ID);
        campaign.setWorkspaceId(WORKSPACE_ID);

        when(campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, campaignId))
                .thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> orchestrationService.triggerSend(campaignId, new SendJobDto.TriggerRequest()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be sent from status");
    }
}

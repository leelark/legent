package com.legent.campaign.service;

import java.util.Optional;



import java.time.Instant;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.dto.SendJobDto;
import com.legent.campaign.event.CampaignEventPublisher;
import com.legent.campaign.mapper.SendJobMapper;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendJobRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrchestrationService Unit Tests")

class OrchestrationServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private SendJobRepository sendJobRepository;
    @Mock private CampaignEventPublisher eventPublisher;
    @Mock private SendJobMapper sendJobMapper;

    @InjectMocks private OrchestrationService orchestrationService;

    private static final String TENANT_ID = "tenant-test";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void triggerSend_Immediate_Success() {
        String campId = "camp-1";
        Campaign campaign = new Campaign();
        campaign.setStatus(Campaign.CampaignStatus.DRAFT);
        campaign.setTenantId(TENANT_ID);

        SendJobDto.TriggerRequest request = new SendJobDto.TriggerRequest(); // null scheduledAt

        SendJob savedJob = new SendJob();
        savedJob.setId("job-1");
        savedJob.setStatus(SendJob.JobStatus.RESOLVING);

        SendJobDto.Response expectedResponse = new SendJobDto.Response();
        expectedResponse.setId("job-1");
        expectedResponse.setStatus(SendJob.JobStatus.RESOLVING);

        when(campaignRepository.findByTenantIdAndIdAndDeletedAtIsNull(TENANT_ID, campId)).thenReturn(Optional.of(campaign));
        when(sendJobRepository.save(any(SendJob.class))).thenReturn(savedJob);
        when(sendJobMapper.toJobResponse(savedJob)).thenReturn(expectedResponse);

        SendJobDto.Response response = orchestrationService.triggerSend(campId, request);

        assertThat(response.getStatus()).isEqualTo(SendJob.JobStatus.RESOLVING);
        verify(campaignRepository).save(campaign);
        assertThat(campaign.getStatus()).isEqualTo(Campaign.CampaignStatus.SENDING);
        verify(eventPublisher).publishAudienceResolutionRequested(eq(TENANT_ID), eq(campId), eq("job-1"), anyList());
    }

    @Test
    void triggerSend_Scheduled_Success() {
        String campId = "camp-1";
        Campaign campaign = new Campaign();
        campaign.setStatus(Campaign.CampaignStatus.DRAFT);
        campaign.setTenantId(TENANT_ID);

        SendJobDto.TriggerRequest request = new SendJobDto.TriggerRequest();
        request.setScheduledAt(Instant.now().plusSeconds(3600));

        SendJob savedJob = new SendJob();
        savedJob.setId("job-1");
        savedJob.setScheduledAt(request.getScheduledAt());

        SendJobDto.Response expectedResponse = new SendJobDto.Response();
        expectedResponse.setId("job-1");

        when(campaignRepository.findByTenantIdAndIdAndDeletedAtIsNull(TENANT_ID, campId)).thenReturn(Optional.of(campaign));
        when(sendJobRepository.save(any(SendJob.class))).thenReturn(savedJob);
        when(sendJobMapper.toJobResponse(savedJob)).thenReturn(expectedResponse);

        orchestrationService.triggerSend(campId, request);

        assertThat(campaign.getStatus()).isEqualTo(Campaign.CampaignStatus.SCHEDULED);
        verify(eventPublisher).publishSendRequested(TENANT_ID, campId, "job-1", request.getScheduledAt());
    }

    @Test
    void triggerSend_InvalidStatus_ThrowsException() {
        String campId = "camp-1";
        Campaign campaign = new Campaign();
        campaign.setStatus(Campaign.CampaignStatus.SENDING); // Not DRAFT
        campaign.setTenantId(TENANT_ID);

        when(campaignRepository.findByTenantIdAndIdAndDeletedAtIsNull(TENANT_ID, campId)).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> orchestrationService.triggerSend(campId, new SendJobDto.TriggerRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Campaign is already SENDING");
    }
}

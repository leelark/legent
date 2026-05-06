package com.legent.campaign.service;

import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.event.CampaignEventPublisher;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendBatchRepository;
import com.legent.campaign.repository.SendJobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchingService Unit Tests")

class BatchingServiceTest {

    @Mock private SendBatchRepository batchRepository;
    @Mock private SendJobRepository jobRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignEventPublisher eventPublisher;
    @Mock private ObjectMapper objectMapper;
    @Mock private ThrottlingService throttlingService;
    @Mock private CampaignStateMachineService stateMachine;

    @InjectMocks private BatchingService batchingService;

    @Test
    void processResolvedAudienceChunk_Success() throws Exception {
        String tenantId = "tenant-1";
        String jobId = "job-1";

        SendJob job = new SendJob();
        job.setId(jobId);
        job.setCampaignId("camp-1");
        job.setStatus(SendJob.JobStatus.RESOLVING);
        job.setTotalTarget(0L);
        job.setWorkspaceId("workspace-test");

        when(jobRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, jobId)).thenReturn(Optional.of(job));
        doAnswer(invocation -> {
            SendJob target = invocation.getArgument(0);
            SendJob.JobStatus next = invocation.getArgument(1);
            target.setStatus(next);
            return null;
        }).when(stateMachine).transitionJob(any(SendJob.class), any(SendJob.JobStatus.class), anyString());
        
        SendBatch mockSavedBatch = new SendBatch();
        mockSavedBatch.setId("batch-1");
        when(batchRepository.save(any(SendBatch.class))).thenReturn(mockSavedBatch);

        // Simulated chunk
        List<Map<String, String>> subs = List.of(
                Map.of("email", "john@gmail.com", "subscriberId", "1"),
                Map.of("email", "jane@yahoo.com", "subscriberId", "2"),
                Map.of("email", "bob@gmail.com", "subscriberId", "3")
        );

        batchingService.processResolvedAudienceChunk(tenantId, jobId, subs, true);

        // Expect 2 batches (gmail.com, yahoo.com)
        ArgumentCaptor<SendBatch> batchCaptor = ArgumentCaptor.forClass(SendBatch.class);
        verify(batchRepository, times(2)).save(batchCaptor.capture());
        
        List<SendBatch> captured = batchCaptor.getAllValues();
        assertThat(captured).hasSize(2);
        
        boolean hasGmail = captured.stream().anyMatch(b -> "gmail.com".equals(b.getDomain()) && b.getBatchSize() == 2);
        boolean hasYahoo = captured.stream().anyMatch(b -> "yahoo.com".equals(b.getDomain()) && b.getBatchSize() == 1);
        
        assertThat(hasGmail).isTrue();
        assertThat(hasYahoo).isTrue();
        assertThat(captured).allMatch(b -> "camp-1".equals(b.getCampaignId()));

        assertThat(job.getTotalTarget()).isEqualTo(3L);
        assertThat(job.getStatus()).isEqualTo(SendJob.JobStatus.SENDING);
        verify(jobRepository).save(job);
        
        verify(eventPublisher, times(2)).publishBatchCreated(eq(tenantId), eq(jobId), anyString());
    }

    @Test
    void processResolvedAudienceChunk_InvalidJobStatus() {
        SendJob job = new SendJob();
        job.setStatus(SendJob.JobStatus.PENDING); // Not Resolving
        job.setWorkspaceId("workspace-test");

        when(jobRepository.findByTenantIdAndIdAndDeletedAtIsNull(anyString(), anyString())).thenReturn(Optional.of(job));

        batchingService.processResolvedAudienceChunk("tenant", "job", List.of(), true);

        verify(batchRepository, never()).save(any());
    }
}

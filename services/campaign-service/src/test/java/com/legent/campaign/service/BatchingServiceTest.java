package com.legent.campaign.service;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.exception.ValidationException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.event.CampaignEventPublisher;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendBatchRepository;
import com.legent.campaign.repository.SendJobRepository;
import com.legent.common.constant.AppConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        when(jobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, "workspace-test", jobId))
                .thenReturn(Optional.of(job));
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

        batchingService.processResolvedAudienceChunk(tenantId, "workspace-test", jobId, subs, true);

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

        when(jobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(job));

        batchingService.processResolvedAudienceChunk("tenant", "workspace-test", "job", List.of(), true);

        verify(batchRepository, never()).save(any());
    }

    @Test
    void processResolvedAudienceChunk_RequeuesExistingBatchesWhenJobAlreadySending() {
        SendJob job = new SendJob();
        job.setId("job-1");
        job.setStatus(SendJob.JobStatus.SENDING);
        job.setWorkspaceId("workspace-test");

        SendBatch pending = new SendBatch();
        pending.setId("batch-1");
        pending.setStatus(SendBatch.BatchStatus.PENDING);
        SendBatch completed = new SendBatch();
        completed.setId("batch-2");
        completed.setStatus(SendBatch.BatchStatus.COMPLETED);

        when(jobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-test", "job-1"))
                .thenReturn(Optional.of(job));
        when(batchRepository.findByTenantIdAndWorkspaceIdAndJobIdAndDeletedAtIsNull(
                "tenant-1", "workspace-test", "job-1")).thenReturn(List.of(pending, completed));

        batchingService.processResolvedAudienceChunk(
                "tenant-1",
                "workspace-test",
                "job-1",
                List.of(Map.of("email", "one@example.com")),
                true);

        verify(eventPublisher).publishBatchCreated("tenant-1", "job-1", "batch-1");
        verify(eventPublisher, never()).publishBatchCreated("tenant-1", "job-1", "batch-2");
        verify(batchRepository, never()).save(any());
    }

    @Test
    void processResolvedAudienceChunk_DoesNotBatchJobFromDifferentWorkspace() {
        when(jobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                "tenant-1", "workspace-requested", "job-1")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> batchingService.processResolvedAudienceChunk(
                        "tenant-1",
                        "workspace-requested",
                        "job-1",
                        List.of(Map.of("email", "one@example.com")),
                        true));

        verify(jobRepository).findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                "tenant-1", "workspace-requested", "job-1");
        verify(batchRepository, never()).save(any());
        verify(eventPublisher, never()).publishBatchCreated(anyString(), anyString(), anyString());
    }

    @Test
    void processResolvedAudienceChunk_SplitsOversizedDomainIntoBoundedDatabaseBatches() {
        String tenantId = "tenant-1";
        String jobId = "job-1";
        SendJob job = new SendJob();
        job.setId(jobId);
        job.setCampaignId("camp-1");
        job.setStatus(SendJob.JobStatus.RESOLVING);
        job.setTotalTarget(0L);
        job.setWorkspaceId("workspace-test");

        ObjectMapper mapper = new ObjectMapper();
        BatchingService service = new BatchingService(
                batchRepository,
                jobRepository,
                campaignRepository,
                eventPublisher,
                mapper,
                stateMachine);

        when(jobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, "workspace-test", jobId))
                .thenReturn(Optional.of(job));
        doAnswer(invocation -> {
            SendJob target = invocation.getArgument(0);
            SendJob.JobStatus next = invocation.getArgument(1);
            target.setStatus(next);
            return null;
        }).when(stateMachine).transitionJob(any(SendJob.class), any(SendJob.JobStatus.class), anyString());
        AtomicInteger batchSequence = new AtomicInteger();
        doAnswer(invocation -> {
            SendBatch batch = invocation.getArgument(0);
            batch.setId("batch-" + batchSequence.incrementAndGet());
            return batch;
        }).when(batchRepository).save(any(SendBatch.class));

        List<Map<String, String>> subscribers = java.util.stream.IntStream
                .range(0, AppConstants.SEND_BATCH_SIZE + 1)
                .mapToObj(i -> Map.of(
                        "email", "user" + i + "@example.com",
                        "subscriberId", "sub-" + i))
                .toList();

        service.processResolvedAudienceChunk(tenantId, "workspace-test", jobId, subscribers, true);

        ArgumentCaptor<SendBatch> batchCaptor = ArgumentCaptor.forClass(SendBatch.class);
        verify(batchRepository, times(2)).save(batchCaptor.capture());
        List<SendBatch> batches = batchCaptor.getAllValues();
        assertThat(batches).extracting(SendBatch::getBatchSize)
                .containsExactly(AppConstants.SEND_BATCH_SIZE, 1);
        assertThat(batches).allMatch(batch -> batch.getBatchSize() <= AppConstants.SEND_BATCH_SIZE);
        assertThat(readPayload(mapper, batches.get(0).getPayload())).hasSize(AppConstants.SEND_BATCH_SIZE);
        assertThat(readPayload(mapper, batches.get(1).getPayload())).hasSize(1);
        assertThat(job.getTotalTarget()).isEqualTo(AppConstants.SEND_BATCH_SIZE + 1L);
        verify(eventPublisher, times(2)).publishBatchCreated(eq(tenantId), eq(jobId), anyString());
        verify(eventPublisher, never()).publishSendProcessing(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void processResolvedAudienceChunk_RethrowsUnexpectedFailure() throws Exception {
        SendJob job = new SendJob();
        job.setId("job-1");
        job.setCampaignId("camp-1");
        job.setStatus(SendJob.JobStatus.RESOLVING);
        job.setTotalTarget(0L);
        job.setWorkspaceId("workspace-test");

        when(jobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-test", "job-1"))
                .thenReturn(Optional.of(job));
        when(objectMapper.writeValueAsString(any())).thenThrow(new ValidationException("payload", "invalid"));

        assertThrows(IllegalStateException.class,
                () -> batchingService.processResolvedAudienceChunk(
                        "tenant-1",
                        "workspace-test",
                        "job-1",
                        List.of(Map.of("email", "one@example.com")),
                        false));
    }

    private List<Map<String, String>> readPayload(ObjectMapper mapper, String payload) {
        try {
            return mapper.readValue(payload, new TypeReference<>() {});
        } catch (Exception e) {
            throw new AssertionError("Batch payload should be valid JSON", e);
        }
    }
}

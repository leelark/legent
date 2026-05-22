package com.legent.campaign.service;

import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.domain.SendJobCheckpoint;
import com.legent.campaign.repository.SendBatchRepository;
import com.legent.campaign.repository.SendJobCheckpointRepository;
import com.legent.campaign.repository.SendJobRepository;
import com.legent.common.exception.NotFoundException;
import com.legent.security.TenantContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendJobCheckpointingServiceTest {

    @Mock private SendJobRepository jobRepository;
    @Mock private SendBatchRepository batchRepository;
    @Mock private SendJobCheckpointRepository checkpointRepository;

    private SendJobCheckpointingService service;

    @BeforeEach
    void setUp() {
        service = new SendJobCheckpointingService(jobRepository, batchRepository, checkpointRepository);
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createCheckpointPersistsWorkspaceScopedCheckpoint() {
        SendJob job = sendJob("job-1", "workspace-1");
        job.setStatus(SendJob.JobStatus.SENDING);
        job.setTotalSent(10L);
        job.setTotalFailed(2L);
        when(jobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "job-1"))
                .thenReturn(Optional.of(job));
        when(checkpointRepository.countByTenantIdAndWorkspaceIdAndJobId("tenant-1", "workspace-1", "job-1"))
                .thenReturn(2L);
        when(checkpointRepository.save(any(SendJobCheckpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SendJobCheckpoint checkpoint = service.createCheckpoint(
                "job-1",
                SendJobCheckpoint.CheckpointType.BATCH,
                "recipient-20",
                20);

        assertThat(checkpoint.getTenantId()).isEqualTo("tenant-1");
        assertThat(checkpoint.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(checkpoint.getSequenceNumber()).isEqualTo(3);
        assertThat(checkpoint.getMetadata()).containsEntry("jobStatus", "SENDING");
        assertThat(job.isCanResume()).isTrue();
        assertThat(job.getLastCheckpointAt()).isNotNull();
        verify(jobRepository).save(job);
    }

    @Test
    void createCheckpointCannotLoadJobFromAnotherWorkspace() {
        when(jobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "job-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createCheckpoint(
                "job-1",
                SendJobCheckpoint.CheckpointType.BATCH,
                "recipient-20",
                20))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(checkpointRepository, batchRepository);
    }

    @Test
    void getLatestCheckpointUsesScopedJobAndCheckpointLookup() {
        SendJob job = sendJob("job-1", "workspace-1");
        SendJobCheckpoint checkpoint = checkpoint("job-1", "workspace-1", 4, 500);
        when(jobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "job-1"))
                .thenReturn(Optional.of(job));
        when(checkpointRepository.findFirstByTenantIdAndWorkspaceIdAndJobIdAndDeletedAtIsNullOrderBySequenceNumberDesc(
                "tenant-1", "workspace-1", "job-1"))
                .thenReturn(Optional.of(checkpoint));

        Optional<SendJobCheckpoint> result = service.getLatestCheckpoint("job-1");

        assertThat(result).containsSame(checkpoint);
    }

    @Test
    void resumeJobCopiesWorkspaceOwnershipToReplacementJob() {
        SendJob original = sendJob("job-1", "workspace-1");
        original.setCanResume(true);
        original.setCampaignId("campaign-1");
        original.setTeamId("team-1");
        original.setOwnershipScope("TEAM");
        original.setTotalTarget(1000L);
        original.setCheckpointInterval(250);
        SendJobCheckpoint checkpoint = checkpoint("job-1", "workspace-1", 5, 400);
        when(jobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "job-1"))
                .thenReturn(Optional.of(original));
        when(checkpointRepository.findFirstByTenantIdAndWorkspaceIdAndJobIdAndDeletedAtIsNullOrderBySequenceNumberDesc(
                "tenant-1", "workspace-1", "job-1"))
                .thenReturn(Optional.of(checkpoint));
        when(jobRepository.save(any(SendJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SendJob resumed = service.resumeJob("job-1");

        assertThat(resumed.getTenantId()).isEqualTo("tenant-1");
        assertThat(resumed.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(resumed.getTeamId()).isEqualTo("team-1");
        assertThat(resumed.getOwnershipScope()).isEqualTo("TEAM");
        assertThat(resumed.getResumedFromJobId()).isEqualTo("job-1");
        assertThat(resumed.getTotalTarget()).isEqualTo(600);
        assertThat(resumed.getCreatedBy()).isEqualTo("user-1");
        assertThat(original.isCanResume()).isFalse();
    }

    @Test
    void markFailedBatchesForRetryOnlyUpdatesCurrentWorkspaceFailedBatches() {
        SendJob job = sendJob("job-1", "workspace-1");
        SendBatch failed = batch("batch-1", "workspace-1", SendBatch.BatchStatus.FAILED, 1);
        SendBatch completed = batch("batch-2", "workspace-1", SendBatch.BatchStatus.COMPLETED, 0);
        when(jobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "job-1"))
                .thenReturn(Optional.of(job));
        when(batchRepository.findByTenantIdAndWorkspaceIdAndJobIdAndDeletedAtIsNull("tenant-1", "workspace-1", "job-1"))
                .thenReturn(List.of(failed, completed));

        service.markFailedBatchesForRetry("job-1");

        ArgumentCaptor<List<SendBatch>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(failed);
        assertThat(failed.getStatus()).isEqualTo(SendBatch.BatchStatus.PENDING);
        assertThat(failed.getRetryCount()).isEqualTo(2);
        assertThat(completed.getStatus()).isEqualTo(SendBatch.BatchStatus.COMPLETED);
    }

    @Test
    void calculateProgressUsesScopedJobLookup() {
        SendJob job = sendJob("job-1", "workspace-1");
        job.setTotalTarget(100L);
        job.setTotalSent(30L);
        job.setTotalFailed(5L);
        job.setTotalBounced(3L);
        job.setTotalSuppressed(2L);
        when(jobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "job-1"))
                .thenReturn(Optional.of(job));

        double progress = service.calculateProgress("job-1");

        assertThat(progress).isEqualTo(40.0);
    }

    @Test
    void missingWorkspaceFailsClosedBeforeRepositoryAccess() {
        TenantContext.setWorkspaceId(null);

        assertThatThrownBy(() -> service.calculateProgress("job-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workspace context is not set");

        verifyNoInteractions(jobRepository, checkpointRepository, batchRepository);
    }

    private SendJob sendJob(String id, String workspaceId) {
        SendJob job = new SendJob();
        job.setId(id);
        job.setTenantId("tenant-1");
        job.setWorkspaceId(workspaceId);
        job.setCampaignId("campaign-1");
        job.setTotalSent(0L);
        job.setTotalFailed(0L);
        job.setTotalBounced(0L);
        job.setTotalSuppressed(0L);
        job.setTotalTarget(0L);
        return job;
    }

    private SendJobCheckpoint checkpoint(String jobId, String workspaceId, int sequenceNumber, long processedCount) {
        SendJobCheckpoint checkpoint = new SendJobCheckpoint();
        checkpoint.setTenantId("tenant-1");
        checkpoint.setWorkspaceId(workspaceId);
        checkpoint.setJobId(jobId);
        checkpoint.setCheckpointType(SendJobCheckpoint.CheckpointType.BATCH);
        checkpoint.setSequenceNumber(sequenceNumber);
        checkpoint.setProcessedCount(processedCount);
        return checkpoint;
    }

    private SendBatch batch(String id, String workspaceId, SendBatch.BatchStatus status, int retryCount) {
        SendBatch batch = new SendBatch();
        batch.setId(id);
        batch.setTenantId("tenant-1");
        batch.setWorkspaceId(workspaceId);
        batch.setJobId("job-1");
        batch.setStatus(status);
        batch.setRetryCount(retryCount);
        return batch;
    }
}

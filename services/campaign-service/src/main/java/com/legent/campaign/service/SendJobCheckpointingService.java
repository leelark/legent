package com.legent.campaign.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.domain.SendJobCheckpoint;
import com.legent.campaign.repository.SendBatchRepository;
import com.legent.campaign.repository.SendJobCheckpointRepository;
import com.legent.campaign.repository.SendJobRepository;
import com.legent.common.exception.NotFoundException;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for send job checkpointing and resume functionality.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendJobCheckpointingService {

    private final SendJobRepository jobRepository;
    private final SendBatchRepository batchRepository;
    private final SendJobCheckpointRepository checkpointRepository;

    /**
     * Create a checkpoint for a send job.
     */
    @Transactional
    public SendJobCheckpoint createCheckpoint(String jobId, SendJobCheckpoint.CheckpointType type,
                                               String lastProcessedId, long processedCount) {
        String tenantId = TenantContext.requireTenantId();

        SendJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("SendJob", jobId));

        int sequenceNumber = (int) checkpointRepository.countByJobId(jobId) + 1;

        SendJobCheckpoint checkpoint = new SendJobCheckpoint();
        checkpoint.setTenantId(tenantId);
        checkpoint.setJobId(jobId);
        checkpoint.setCheckpointType(type);
        checkpoint.setSequenceNumber(sequenceNumber);
        checkpoint.setLastProcessedId(lastProcessedId);
        checkpoint.setProcessedCount(processedCount);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("jobStatus", job.getStatus().name());
        metadata.put("totalSent", job.getTotalSent());
        metadata.put("totalFailed", job.getTotalFailed());
        checkpoint.setMetadata(metadata);

        checkpointRepository.save(checkpoint);

        // Update job checkpoint timestamp
        job.setLastCheckpointAt(Instant.now());
        job.setCanResume(true);
        jobRepository.save(job);

        log.info("Created checkpoint {} for job {}, processed {} items",
                sequenceNumber, jobId, processedCount);

        return checkpoint;
    }

    /**
     * Get the latest checkpoint for a job.
     */
    @Transactional(readOnly = true)
    public Optional<SendJobCheckpoint> getLatestCheckpoint(String jobId) {
        return checkpointRepository.findLatestCheckpoint(jobId);
    }

    /**
     * Get all checkpoints for a job.
     */
    @Transactional(readOnly = true)
    public List<SendJobCheckpoint> getJobCheckpoints(String jobId) {
        return checkpointRepository.findByJobIdOrderBySequenceNumberDesc(jobId);
    }

    /**
     * Resume a failed job from its last checkpoint.
     */
    @Transactional
    public SendJob resumeJob(String jobId) {
        String userId = TenantContext.getUserId();
        String tenantId = TenantContext.requireTenantId();

        SendJob originalJob = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("SendJob", jobId));

        if (!originalJob.isCanResume()) {
            throw new IllegalStateException("Job cannot be resumed");
        }

        // Get the latest checkpoint
        SendJobCheckpoint checkpoint = checkpointRepository.findLatestCheckpoint(jobId)
                .orElseThrow(() -> new NotFoundException("No checkpoint found for job"));

        // Create a new job that continues from checkpoint
        SendJob resumedJob = new SendJob();
        resumedJob.setTenantId(tenantId);
        resumedJob.setCampaignId(originalJob.getCampaignId());
        resumedJob.setStatus(SendJob.JobStatus.PENDING);
        resumedJob.setResumedFromJobId(jobId);
        resumedJob.setTotalTarget(originalJob.getTotalTarget() - checkpoint.getProcessedCount());
        resumedJob.setCanResume(true);
        resumedJob.setCheckpointInterval(originalJob.getCheckpointInterval());
        resumedJob.setCreatedBy(userId);

        jobRepository.save(resumedJob);

        // Mark original job as recovered
        originalJob.setCanResume(false);
        jobRepository.save(originalJob);

        log.info("Resumed job {} as new job {} from checkpoint {} (processed {} items)",
                jobId, resumedJob.getId(), checkpoint.getSequenceNumber(), checkpoint.getProcessedCount());

        return resumedJob;
    }

    /**
     * Mark failed batches for retry.
     */
    @Transactional
    public void markFailedBatchesForRetry(String jobId) {
        List<SendBatch> failedBatches = batchRepository.findByJobIdAndStatus(jobId, SendBatch.BatchStatus.FAILED);

        for (SendBatch batch : failedBatches) {
            batch.setStatus(SendBatch.BatchStatus.PENDING);
            batch.setRetryCount(batch.getRetryCount() + 1);
        }

        batchRepository.saveAll(failedBatches);

        log.info("Marked {} failed batches for retry in job {}", failedBatches.size(), jobId);
    }

    /**
     * Calculate job progress percentage.
     */
    @Transactional(readOnly = true)
    public double calculateProgress(String jobId) {
        SendJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("SendJob", jobId));

        if (job.getTotalTarget() == 0) {
            return 100.0;
        }

        long totalProcessed = job.getTotalSent() + job.getTotalFailed() + job.getTotalBounced() + job.getTotalSuppressed();
        return (totalProcessed * 100.0) / job.getTotalTarget();
    }

    /**
     * Auto-create checkpoint based on interval.
     */
    @Transactional
    public void autoCheckpoint(String jobId, String lastProcessedId, long processedCount) {
        SendJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("SendJob", jobId));

        int interval = job.getCheckpointInterval() != null ? job.getCheckpointInterval() : 1000;

        // Only create checkpoint if we've processed enough since last checkpoint
        long lastCheckpointCount = checkpointRepository.findLatestCheckpoint(jobId)
                .map(SendJobCheckpoint::getProcessedCount)
                .orElse(0L);

        if (processedCount - lastCheckpointCount >= interval) {
            createCheckpoint(jobId, SendJobCheckpoint.CheckpointType.BATCH, lastProcessedId, processedCount);
        }
    }
}

package com.legent.campaign.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import java.util.List;

import java.util.Map;
import java.util.ArrayList;

import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.event.CampaignEventPublisher;
import com.legent.campaign.repository.SendBatchRepository;
import com.legent.campaign.repository.SendJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchingService {

    private final SendBatchRepository batchRepository;
    private final SendJobRepository jobRepository;
    private final CampaignEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processResolvedAudienceChunk(String tenantId, String jobId, List<Map<String, String>> subscribers, boolean isLastChunk) {
        try {
            SendJob job = jobRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, jobId).orElseThrow();
            if (job.getStatus() != SendJob.JobStatus.RESOLVING) {
                log.warn("Ignoring chunk for job {} in state {}", jobId, job.getStatus());
                return;
            }

            // Group by email domain
            Map<String, List<Map<String, String>>> domainGroups = subscribers.stream()
                .collect(Collectors.groupingBy(s -> extractDomain(s.get("email"))));
            List<String> createdBatchIds = new ArrayList<>();

            for (Map.Entry<String, List<Map<String, String>>> entry : domainGroups.entrySet()) {
                String domain = entry.getKey();
                List<Map<String, String>> domainSubscribers = entry.getValue();

                // Split into batches
                for (int i = 0; i < domainSubscribers.size(); i += AppConstants.SEND_BATCH_SIZE) {
                    List<Map<String, String>> batchData = domainSubscribers.subList(i, Math.min(i + AppConstants.SEND_BATCH_SIZE, domainSubscribers.size()));
                    
                    SendBatch batch = new SendBatch();
                    batch.setTenantId(tenantId);
                    batch.setJobId(jobId);
                    batch.setCampaignId(job.getCampaignId());
                    batch.setDomain(domain);
                    batch.setBatchSize(batchData.size());
                    batch.setStatus(SendBatch.BatchStatus.PENDING);
                    batch.setPayload(objectMapper.writeValueAsString(batchData));
                    
                    batch = batchRepository.save(batch);
                    createdBatchIds.add(batch.getId());
                    
                    // Increment total target
                    job.setTotalTarget(job.getTotalTarget() + batchData.size());
                }
            }

            if (isLastChunk) {
                job.setStatus(SendJob.JobStatus.BATCHING); // Will transition to SENDING when ready
                jobRepository.save(job);
                log.info("Batching complete for job {}. Total targets: {}", jobId, job.getTotalTarget());
            } else {
                jobRepository.save(job);
            }

            publishBatchEventsAfterCommit(tenantId, jobId, createdBatchIds);
        } catch (Exception e) {
            log.error("Failed to process chunk for job {}", jobId, e);
        }
    }

    private String extractDomain(String email) {
        if (email == null || !email.contains("@")) return "unknown";
        return email.substring(email.lastIndexOf("@") + 1).toLowerCase();
    }

    private void publishBatchEventsAfterCommit(String tenantId, String jobId, List<String> batchIds) {
        if (batchIds.isEmpty()) {
            return;
        }

        Runnable publishAction = () -> batchIds.forEach(batchId ->
                eventPublisher.publishBatchCreated(tenantId, jobId, batchId));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishAction.run();
                }
            });
        } else {
            publishAction.run();
        }
    }
}

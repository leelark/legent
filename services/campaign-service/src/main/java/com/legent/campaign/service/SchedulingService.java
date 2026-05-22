package com.legent.campaign.service;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.event.CampaignEventPublisher;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendJobRepository;
import com.legent.common.exception.ValidationException;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final SendJobRepository sendJobRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignEventPublisher eventPublisher;
    private final CampaignStateMachineService stateMachine;
    private final CampaignLockService campaignLockService;

    @Value("${legent.campaign.scheduler.due-job-page-size:100}")
    private int dueJobPageSize;

    @Scheduled(fixedDelayString = "${legent.campaign.scheduler.delay:60000}")
    @Transactional
    public void processScheduledJobs() {
        log.debug("Checking for scheduled send jobs...");
        Instant now = Instant.now();
        int pageSize = Math.max(1, dueJobPageSize);
        List<SendJob> dueJobs = sendJobRepository.findByStatusAndScheduledAtBeforeAndDeletedAtIsNullOrderByScheduledAtAscCreatedAtAsc(
                SendJob.JobStatus.PENDING, now, PageRequest.of(0, pageSize));
        
        for (SendJob job : dueJobs) {
            try {
                log.info("Processing due job: {}", job.getId());
                String tenantId = requireJobScope(job.getTenantId(), "tenantId", job.getId());
                String workspaceId = requireJobScope(job.getWorkspaceId(), "workspaceId", job.getId());
                Instant claimTime = Instant.now();
                int claimed = sendJobRepository.claimDueScheduledJob(
                        tenantId,
                        workspaceId,
                        job.getId(),
                        SendJob.JobStatus.PENDING,
                        SendJob.JobStatus.RESOLVING,
                        now,
                        claimTime);
                if (claimed == 0) {
                    log.debug("Skipping due job {} because another scheduler claimed or changed it", job.getId());
                    continue;
                }
                stateMachine.transitionJob(job, SendJob.JobStatus.RESOLVING, "Scheduled job due");
                job.setStartedAt(claimTime);

                Campaign campaign = campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                        tenantId,
                        workspaceId,
                        java.util.Objects.requireNonNull(job.getCampaignId())
                ).orElse(null);
                if (campaign != null) {
                    if (campaign.isApprovalRequired() && campaign.getStatus() != Campaign.CampaignStatus.APPROVED) {
                        throw new ValidationException("campaign.status", "Scheduled campaign requires approval before send");
                    }
                    if (campaign.isApprovalRequired()) {
                        campaignLockService.validateActiveLock(campaign);
                    }
                    stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.SENDING, "Scheduled send started");
                    campaignRepository.save(campaign);

                    List<Map<String, String>> audienceList = campaign.getAudiences().stream()
                            .filter(a -> a != null && a.getAudienceType() != null && a.getAudienceId() != null && !a.getAudienceId().isBlank() && a.getAction() != null)
                            .map(a -> Map.of("type", a.getAudienceType().name(), "id", a.getAudienceId(), "action", a.getAction().name()))
                            .collect(Collectors.toList());
                    if (audienceList.isEmpty()) {
                        throw new ValidationException("campaign.audiences", "Audience definitions are invalid");
                    }

                    withJobTenantContext(tenantId, workspaceId, () -> eventPublisher.publishAudienceResolutionRequested(
                            tenantId, campaign.getId(), job.getId(), audienceList));
                } else {
                    log.error("Campaign {} not found for job {}", job.getCampaignId(), job.getId());
                    stateMachine.transitionJob(job, SendJob.JobStatus.FAILED, "Campaign missing for scheduled job");
                    sendJobRepository.save(job);
                }
            } catch (Exception e) {
                log.error("Failed to transition job {} to resolving", job.getId(), e);
                try {
                    if (job.getStatus() != SendJob.JobStatus.FAILED && job.getStatus() != SendJob.JobStatus.CANCELLED) {
                        stateMachine.transitionJob(job, SendJob.JobStatus.FAILED, e.getMessage());
                        sendJobRepository.save(job);
                    }
                } catch (Exception ignored) {
                    log.debug("Job {} already transitioned, skipping failure transition", job.getId());
                }
            }
        }
    }

    private String requireJobScope(String value, String field, String jobId) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("sendJob." + field, "Scheduled job " + jobId + " is missing " + field);
        }
        return value;
    }

    private void withJobTenantContext(String tenantId, String workspaceId, Runnable action) {
        String previousTenantId = TenantContext.getTenantId();
        String previousUserId = TenantContext.getUserId();
        String previousWorkspaceId = TenantContext.getWorkspaceId();
        String previousEnvironmentId = TenantContext.getEnvironmentId();
        String previousRequestId = TenantContext.getRequestId();
        String previousCorrelationId = TenantContext.getCorrelationId();
        TenantContext.clear();
        TenantContext.setTenantId(tenantId);
        TenantContext.setWorkspaceId(workspaceId);
        try {
            action.run();
        } finally {
            restoreTenantContext(
                    previousTenantId,
                    previousUserId,
                    previousWorkspaceId,
                    previousEnvironmentId,
                    previousRequestId,
                    previousCorrelationId);
        }
    }

    private void restoreTenantContext(String tenantId,
                                      String userId,
                                      String workspaceId,
                                      String environmentId,
                                      String requestId,
                                      String correlationId) {
        TenantContext.clear();
        if (tenantId != null) {
            TenantContext.setTenantId(tenantId);
        }
        if (userId != null) {
            TenantContext.setUserId(userId);
        }
        if (workspaceId != null) {
            TenantContext.setWorkspaceId(workspaceId);
        }
        if (environmentId != null) {
            TenantContext.setEnvironmentId(environmentId);
        }
        if (requestId != null) {
            TenantContext.setRequestId(requestId);
        }
        if (correlationId != null) {
            TenantContext.setCorrelationId(correlationId);
        }
    }
}

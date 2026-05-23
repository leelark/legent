package com.legent.campaign.service;

import java.time.Instant;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.dto.CampaignDto;
import com.legent.campaign.dto.SendJobDto;
import com.legent.campaign.event.CampaignEventPublisher;
import com.legent.campaign.mapper.SendJobMapper;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendBatchRepository;
import com.legent.campaign.repository.SendJobRepository;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestrationService {

    private static final int BATCH_REQUEUE_PAGE_SIZE = 500;

    private final CampaignRepository campaignRepository;
    private final SendBatchRepository sendBatchRepository;
    private final SendJobRepository sendJobRepository;
    private final CampaignEventPublisher eventPublisher;
    private final SendJobMapper sendJobMapper;
    private final CampaignStateMachineService stateMachine;
    private final CampaignLockService campaignLockService;
    private final CampaignEngineService campaignEngineService;

    @Transactional
    public SendJobDto.Response triggerSend(String campaignId, SendJobDto.TriggerRequest request) {
        if (request == null) {
            request = new SendJobDto.TriggerRequest();
        }
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        Campaign campaign = campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign", campaignId));
        if (!Set.of(Campaign.CampaignStatus.DRAFT, Campaign.CampaignStatus.APPROVED, Campaign.CampaignStatus.SCHEDULED, Campaign.CampaignStatus.FAILED)
                .contains(campaign.getStatus())) {
            throw new ValidationException("campaign.status", "Campaign cannot be sent from status: " + campaign.getStatus());
        }

        if (campaign.isApprovalRequired()
                && campaign.getStatus() != Campaign.CampaignStatus.APPROVED
                && !isAdminBypass()) {
            throw new ValidationException("campaign.status", "Campaign must be APPROVED before send");
        }
        if (campaign.getAudiences() == null || campaign.getAudiences().isEmpty()) {
            throw new ValidationException("campaign.audiences", "At least one audience is required before triggering a send");
        }
        if (campaign.isApprovalRequired() && campaign.getStatus() == Campaign.CampaignStatus.APPROVED) {
            campaignLockService.validateActiveLock(campaign);
        }
        var preflight = campaignEngineService.preflight(campaignId);
        if (!preflight.isSendAllowed()) {
            throw new ValidationException("campaign.preflight", String.join("; ", preflight.getErrors()));
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            request.setIdempotencyKey(defaultSendIdempotencyKey(campaignId, request));
        }
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            var existing = sendJobRepository.findByTenantIdAndWorkspaceIdAndIdempotencyKeyAndDeletedAtIsNull(
                    tenantId, workspaceId, request.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                return sendJobMapper.toJobResponse(existing.get());
            }
        }

        SendJob job = new SendJob();
        job.setTenantId(tenantId);
        job.setWorkspaceId(workspaceId);
        job.setTeamId(campaign.getTeamId());
        job.setOwnershipScope(campaign.getOwnershipScope());
        job.setCampaignId(campaignId);
        job.setScheduledAt(request.getScheduledAt());
        job.setTriggerSource(request.getTriggerSource());
        job.setTriggerReference(request.getTriggerReference());
        job.setIdempotencyKey(request.getIdempotencyKey());
        
        if (request.getScheduledAt() == null || request.getScheduledAt().isBefore(Instant.now())) {
            job.setStatus(SendJob.JobStatus.RESOLVING);
            job.setStartedAt(Instant.now());
            stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.SENDING, "Immediate send triggered");
            campaign.setScheduledAt(null);
        } else {
            job.setStatus(SendJob.JobStatus.PENDING);
            stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.SCHEDULED, "Scheduled send configured");
            campaign.setScheduledAt(request.getScheduledAt());
        }
        if (request.getTriggerSource() != null && !request.getTriggerSource().isBlank()) {
            campaign.setTriggerSource(request.getTriggerSource());
        }
        if (request.getTriggerReference() != null && !request.getTriggerReference().isBlank()) {
            campaign.setTriggerReference(request.getTriggerReference());
        }

        job = sendJobRepository.save(job);
        campaignRepository.save(campaign);

        if (job.getStatus() == SendJob.JobStatus.RESOLVING) {
            List<Map<String, String>> audienceList = toAudienceList(campaign);
            if (audienceList.isEmpty()) {
                throw new ValidationException("campaign.audiences", "Audience definitions are invalid");
            }
            
            eventPublisher.publishAudienceResolutionRequested(tenantId, campaignId, job.getId(), audienceList);
            log.info("Triggered immediate send for job: {}", job.getId());
        }
        if (job.getStatus() == SendJob.JobStatus.PENDING) {
            log.info("Scheduled send for job: {} at {}", job.getId(), job.getScheduledAt());
        }

        return sendJobMapper.toJobResponse(job);
    }

    @Transactional
    public SendJobDto.Response triggerFromAutomation(String campaignId, CampaignDto.TriggerLaunchRequest request) {
        SendJobDto.TriggerRequest triggerRequest = new SendJobDto.TriggerRequest();
        triggerRequest.setScheduledAt(request != null ? request.getScheduledAt() : null);
        triggerRequest.setTriggerSource(request != null && request.getTriggerSource() != null
                ? request.getTriggerSource() : "AUTOMATION");
        triggerRequest.setTriggerReference(request != null ? request.getTriggerReference() : null);
        triggerRequest.setIdempotencyKey(request != null ? request.getIdempotencyKey() : null);
        return triggerSend(campaignId, triggerRequest);
    }
    
    @Transactional(readOnly = true)
    public Page<SendJobDto.Response> getJobsForCampaign(String campaignId, Pageable pageable) {
        return sendJobRepository.findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(
                        TenantContext.requireTenantId(),
                        TenantContext.requireWorkspaceId(),
                        campaignId,
                        pageable)
                .map(sendJobMapper::toJobResponse);
    }

    @Transactional(readOnly = true)
    public SendJobDto.Response getJobStatus(String jobId) {
        return sendJobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                        TenantContext.requireTenantId(),
                        TenantContext.requireWorkspaceId(),
                        jobId)
                .map(sendJobMapper::toJobResponse)
                .orElseThrow(() -> new NotFoundException("SendJob", jobId));
    }

    @Transactional
    public SendJobDto.Response pauseCampaignSend(String campaignId, String reason) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        SendJob activeJob = latestActiveJob(tenantId, workspaceId, campaignId);
        stateMachine.transitionJob(activeJob, SendJob.JobStatus.PAUSED, reason);
        sendJobRepository.save(activeJob);

        Campaign campaign = campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign", campaignId));
        stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.PAUSED, reason);
        campaignRepository.save(campaign);
        return sendJobMapper.toJobResponse(activeJob);
    }

    @Transactional
    public SendJobDto.Response resumeCampaignSend(String campaignId, String comments) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        SendJob pausedJob = sendJobRepository.findByTenantIdAndWorkspaceIdAndCampaignIdAndStatusInAndDeletedAtIsNull(
                        tenantId,
                        workspaceId,
                        campaignId,
                        List.of(SendJob.JobStatus.PAUSED))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ValidationException("sendJob.status", "No paused send job found"));

        stateMachine.transitionJob(pausedJob, SendJob.JobStatus.SENDING, comments);
        pausedJob.setPausedAt(null);
        sendJobRepository.save(pausedJob);

        Campaign campaign = campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign", campaignId));
        stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.SENDING, comments);
        campaignRepository.save(campaign);

        publishBatchCreatedForStatusPages(
                tenantId,
                workspaceId,
                pausedJob.getId(),
                List.of(SendBatch.BatchStatus.PENDING, SendBatch.BatchStatus.PARTIAL));
        return sendJobMapper.toJobResponse(pausedJob);
    }

    @Transactional
    public SendJobDto.Response cancelCampaignSend(String campaignId, String reason) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        SendJob activeJob = latestActiveJob(tenantId, workspaceId, campaignId);
        stateMachine.transitionJob(activeJob, SendJob.JobStatus.CANCELLED, reason);
        sendJobRepository.save(activeJob);

        Campaign campaign = campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign", campaignId));
        stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.CANCELLED, reason);
        campaignRepository.save(campaign);
        return sendJobMapper.toJobResponse(activeJob);
    }

    @Transactional
    public SendJobDto.Response retryJob(String jobId, String reason) {
        SendJob job = sendJobRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                        TenantContext.requireTenantId(),
                        TenantContext.requireWorkspaceId(),
                        jobId)
                .orElseThrow(() -> new NotFoundException("SendJob", jobId));
        stateMachine.transitionJob(job, SendJob.JobStatus.RETRYING, reason);
        sendJobRepository.save(job);

        int retryable = requeueRetryableBatchPages(job);
        if (retryable == 0) {
            throw new ValidationException("sendBatch.status", "No retryable batches found");
        }
        job.setStatus(SendJob.JobStatus.SENDING);
        sendJobRepository.save(job);
        return sendJobMapper.toJobResponse(job);
    }

    @Transactional
    public SendJobDto.Response resendCampaign(String campaignId, String reason) {
        Campaign campaign = campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                        TenantContext.requireTenantId(),
                        TenantContext.requireWorkspaceId(),
                        campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign", campaignId));
        stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.APPROVED, reason);
        campaignRepository.save(campaign);
        SendJobDto.TriggerRequest request = new SendJobDto.TriggerRequest();
        request.setTriggerSource("RESEND");
        request.setTriggerReference(campaignId);
        String requestId = TenantContext.getRequestId();
        request.setIdempotencyKey("resend:" + campaignId + ":" + (requestId != null && !requestId.isBlank()
                ? requestId
                : Integer.toHexString(String.valueOf(reason).hashCode())));
        return triggerSend(campaignId, request);
    }

    private String defaultSendIdempotencyKey(String campaignId, SendJobDto.TriggerRequest request) {
        String requestId = TenantContext.getRequestId();
        if (requestId != null && !requestId.isBlank()) {
            return "send:" + campaignId + ":" + requestId;
        }
        String source = request.getTriggerSource() == null ? "MANUAL" : request.getTriggerSource();
        String reference = request.getTriggerReference() == null ? "" : request.getTriggerReference();
        String scheduled = request.getScheduledAt() == null ? "immediate" : request.getScheduledAt().toString();
        return "send:" + campaignId + ":" + source + ":" + reference + ":" + scheduled;
    }

    private SendJob latestActiveJob(String tenantId, String workspaceId, String campaignId) {
        return sendJobRepository.findByTenantIdAndWorkspaceIdAndCampaignIdAndStatusInAndDeletedAtIsNull(
                        tenantId,
                        workspaceId,
                        campaignId,
                        List.of(SendJob.JobStatus.PENDING, SendJob.JobStatus.RESOLVING, SendJob.JobStatus.BATCHING,
                                SendJob.JobStatus.SENDING, SendJob.JobStatus.RETRYING, SendJob.JobStatus.PAUSED))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ValidationException("sendJob.status", "No active send job found"));
    }

    private int requeueRetryableBatchPages(SendJob job) {
        List<SendBatch.BatchStatus> retryableStatuses = List.of(
                SendBatch.BatchStatus.FAILED,
                SendBatch.BatchStatus.PARTIAL);
        int retryable = 0;
        String afterBatchId = null;
        while (true) {
            List<String> batchIds = sendBatchRepository.findIdsByTenantWorkspaceJobAndStatusesAfterId(
                    job.getTenantId(),
                    job.getWorkspaceId(),
                    job.getId(),
                    retryableStatuses,
                    afterBatchId,
                    PageRequest.of(0, BATCH_REQUEUE_PAGE_SIZE));
            if (batchIds.isEmpty()) {
                break;
            }
            for (String batchId : batchIds) {
                int updated = sendBatchRepository.markScopedBatchIdForRetry(
                        job.getTenantId(),
                        job.getWorkspaceId(),
                        job.getId(),
                        batchId,
                        retryableStatuses,
                        SendBatch.BatchStatus.PENDING,
                        Instant.now());
                if (updated == 1) {
                    eventPublisher.publishBatchCreated(job.getTenantId(), job.getId(), batchId);
                    retryable++;
                } else {
                    log.debug("Skipping retry event for batch {} because it was not claimable for job {}", batchId, job.getId());
                }
            }
            afterBatchId = batchIds.get(batchIds.size() - 1);
            if (batchIds.size() < BATCH_REQUEUE_PAGE_SIZE) {
                break;
            }
        }
        return retryable;
    }

    private int publishBatchCreatedForStatusPages(String tenantId,
                                                  String workspaceId,
                                                  String jobId,
                                                  List<SendBatch.BatchStatus> statuses) {
        int published = 0;
        String afterBatchId = null;
        while (true) {
            List<String> batchIds = sendBatchRepository.findIdsByTenantWorkspaceJobAndStatusesAfterId(
                    tenantId,
                    workspaceId,
                    jobId,
                    statuses,
                    afterBatchId,
                    PageRequest.of(0, BATCH_REQUEUE_PAGE_SIZE));
            if (batchIds.isEmpty()) {
                break;
            }
            batchIds.forEach(batchId -> eventPublisher.publishBatchCreated(tenantId, jobId, batchId));
            published += batchIds.size();
            afterBatchId = batchIds.get(batchIds.size() - 1);
            if (batchIds.size() < BATCH_REQUEUE_PAGE_SIZE) {
                break;
            }
        }
        return published;
    }

    private List<Map<String, String>> toAudienceList(Campaign campaign) {
        return campaign.getAudiences().stream()
                .filter(a -> a != null && a.getAudienceType() != null && a.getAudienceId() != null && !a.getAudienceId().isBlank() && a.getAction() != null)
                .map(a -> Map.of("type", a.getAudienceType().name(), "id", a.getAudienceId(), "action", a.getAction().name()))
                .collect(Collectors.toList());
    }

    private boolean isAdminBypass() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equalsIgnoreCase(authority.getAuthority()));
    }
}

package com.legent.campaign.service;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.event.CampaignEventPublisher;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendJobRepository;
import com.legent.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Scheduled(fixedDelayString = "${legent.campaign.scheduler.delay:60000}")
    @Transactional
    public void processScheduledJobs() {
        log.debug("Checking for scheduled send jobs...");
        
        List<SendJob> dueJobs = sendJobRepository.findByStatusAndScheduledAtBefore(
                SendJob.JobStatus.PENDING, Instant.now());
        
        for (SendJob job : dueJobs) {
            try {
                log.info("Processing due job: {}", job.getId());
                
                stateMachine.transitionJob(job, SendJob.JobStatus.RESOLVING, "Scheduled job due");
                job.setStartedAt(Instant.now());
                sendJobRepository.saveAndFlush(job);

                Campaign campaign = campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                        job.getTenantId(),
                        job.getWorkspaceId(),
                        java.util.Objects.requireNonNull(job.getCampaignId())
                ).orElse(null);
                if (campaign != null) {
                    if (campaign.isApprovalRequired() && campaign.getStatus() != Campaign.CampaignStatus.APPROVED) {
                        throw new ValidationException("campaign.status", "Scheduled campaign requires approval before send");
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

                    eventPublisher.publishAudienceResolutionRequested(
                            job.getTenantId(), campaign.getId(), job.getId(), audienceList);
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
}

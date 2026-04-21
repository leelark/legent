package com.legent.campaign.service;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.event.CampaignEventPublisher;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendJobRepository;
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

    @Scheduled(fixedDelayString = "${legent.campaign.scheduler.delay:60000}")
    @Transactional
    public void processScheduledJobs() {
        log.debug("Checking for scheduled send jobs...");
        
        List<SendJob> dueJobs = sendJobRepository.findByStatusAndScheduledAtBefore(
                SendJob.JobStatus.PENDING, Instant.now());
        
        for (SendJob job : dueJobs) {
            try {
                log.info("Processing due job: {}", job.getId());
                
                job.setStatus(SendJob.JobStatus.RESOLVING);
                job.setStartedAt(Instant.now());
                sendJobRepository.save(job);

                Campaign campaign = campaignRepository.findById(java.util.Objects.requireNonNull(job.getCampaignId())).orElse(null);
                if (campaign != null) {
                    campaign.setStatus(Campaign.CampaignStatus.SENDING);
                    campaignRepository.save(campaign);

                    List<Map<String, String>> audienceList = campaign.getAudiences().stream()
                            .map(a -> Map.of("type", a.getAudienceType().name(), "id", a.getAudienceId()))
                            .collect(Collectors.toList());

                    eventPublisher.publishAudienceResolutionRequested(
                            job.getTenantId(), campaign.getId(), job.getId(), audienceList);
                } else {
                    log.error("Campaign {} not found for job {}", job.getCampaignId(), job.getId());
                    job.setStatus(SendJob.JobStatus.FAILED);
                    sendJobRepository.save(job);
                }
            } catch (Exception e) {
                log.error("Failed to transition job {} to resolving", job.getId(), e);
            }
        }
    }
}

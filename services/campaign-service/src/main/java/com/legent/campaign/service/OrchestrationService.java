package com.legent.campaign.service;

import java.time.Instant;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.dto.SendJobDto;
import com.legent.campaign.event.CampaignEventPublisher;
import com.legent.campaign.mapper.SendJobMapper;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.campaign.repository.SendJobRepository;
import com.legent.common.exception.NotFoundException;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestrationService {

    private final CampaignRepository campaignRepository;
    private final SendJobRepository sendJobRepository;
    private final CampaignEventPublisher eventPublisher;
    private final SendJobMapper sendJobMapper;

    @Transactional
    public SendJobDto.Response triggerSend(String campaignId, SendJobDto.TriggerRequest request) {
        String tenantId = TenantContext.getTenantId();
        Campaign campaign = campaignRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign", campaignId));

        if (campaign.getStatus() != Campaign.CampaignStatus.DRAFT) {
            throw new IllegalStateException("Campaign is already " + campaign.getStatus());
        }

        SendJob job = new SendJob();
        job.setTenantId(tenantId);
        job.setCampaignId(campaignId);
        job.setScheduledAt(request.getScheduledAt());
        
        if (request.getScheduledAt() == null || request.getScheduledAt().isBefore(Instant.now())) {
            job.setStatus(SendJob.JobStatus.RESOLVING);
            job.setStartedAt(Instant.now());
            campaign.setStatus(Campaign.CampaignStatus.SENDING);
        } else {
            job.setStatus(SendJob.JobStatus.PENDING);
            campaign.setStatus(Campaign.CampaignStatus.SCHEDULED);
        }

        job = sendJobRepository.save(job);
        campaignRepository.save(campaign);

        if (job.getStatus() == SendJob.JobStatus.RESOLVING) {
            List<Map<String, String>> audienceList = campaign.getAudiences().stream()
                    .map(a -> Map.of("type", a.getAudienceType().name(), "id", a.getAudienceId()))
                    .collect(Collectors.toList());
            
            eventPublisher.publishAudienceResolutionRequested(tenantId, campaignId, job.getId(), audienceList);
            log.info("Triggered immediate send for job: {}", job.getId());
        } else {
            eventPublisher.publishSendRequested(tenantId, campaignId, job.getId(), job.getScheduledAt());
            log.info("Scheduled send for job: {} at {}", job.getId(), job.getScheduledAt());
        }

        return sendJobMapper.toResponse(job);
    }
    
    @Transactional(readOnly = true)
    public Page<SendJobDto.Response> getJobsForCampaign(String campaignId, Pageable pageable) {
        return sendJobRepository.findByTenantIdAndCampaignIdAndDeletedAtIsNull(TenantContext.getTenantId(), campaignId, pageable)
                .map(sendJobMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public SendJobDto.Response getJobStatus(String jobId) {
        return sendJobRepository.findByTenantIdAndIdAndDeletedAtIsNull(TenantContext.getTenantId(), jobId)
                .map(sendJobMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("SendJob", jobId));
    }
}

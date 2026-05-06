package com.legent.campaign.service;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.dto.CampaignDto;
import com.legent.campaign.mapper.CampaignMapper;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignMapper campaignMapper;
    private final CampaignStateMachineService stateMachine;

    @Value("${legent.campaign.approval.default-required:true}")
    private boolean defaultApprovalRequired;

    @Transactional(readOnly = true)
    public Page<CampaignDto.Response> search(String search, Pageable pageable) {
        String tenantId = TenantContext.getTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        Page<Campaign> campaigns;

        if (search == null || search.isBlank()) {
            campaigns = campaignRepository.findByTenantIdAndWorkspaceIdAndDeletedAtIsNull(tenantId, workspaceId, pageable);
        } else {
            campaigns = campaignRepository.findByTenantIdAndWorkspaceIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
                    tenantId,
                    workspaceId,
                    search.trim(),
                    pageable
            );
        }

        return campaigns
                .map(campaignMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CampaignDto.Response getById(String id) {
        return campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                        TenantContext.requireTenantId(),
                        TenantContext.requireWorkspaceId(),
                        id)
                .map(campaignMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Campaign", id));
    }

    @Transactional
    public CampaignDto.Response create(CampaignDto.CreateRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        Campaign campaign = campaignMapper.toEntity(request);
        campaign.setTenantId(tenantId);
        campaign.setWorkspaceId(workspaceId);
        campaign.setApprovalRequired(request.getApprovalRequired() != null ? request.getApprovalRequired() : defaultApprovalRequired);
        if (campaign.getType() == null) {
            campaign.setType(Campaign.CampaignType.STANDARD);
        }
        applyDefaults(campaign);
        if (request.getAudiences() != null && !request.getAudiences().isEmpty()) {
            for (CampaignDto.AudienceRequest ar : request.getAudiences()) {
                campaign.addAudience(ar.getAudienceType(), ar.getAudienceId(), ar.getAction());
            }
        }
        return campaignMapper.toResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignDto.Response update(String id, CampaignDto.UpdateRequest request) {
        Campaign campaign = findScoped(id);

        if (campaign.getStatus() != Campaign.CampaignStatus.DRAFT
                && campaign.getStatus() != Campaign.CampaignStatus.APPROVED) {
            throw new ValidationException("campaign.status", "Cannot update campaign in status: " + campaign.getStatus());
        }

        campaignMapper.updateEntity(request, campaign);

        if (request.getAudiences() != null) {
            campaign.getAudiences().clear();
            for (CampaignDto.AudienceRequest audienceRequest : request.getAudiences()) {
                campaign.addAudience(audienceRequest.getAudienceType(), audienceRequest.getAudienceId(), audienceRequest.getAction());
            }
        }
        applyDefaults(campaign);

        return campaignMapper.toResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public void delete(String id) {
        Campaign campaign = findScoped(id);
        campaign.softDelete();
        campaignRepository.save(campaign);
    }

    @Transactional
    public CampaignDto.Response archive(String id, String reason) {
        Campaign campaign = findScoped(id);
        stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.ARCHIVED, reason);
        return campaignMapper.toResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignDto.Response restore(String id, String comments) {
        Campaign campaign = findScoped(id);
        stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.DRAFT, comments);
        return campaignMapper.toResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignDto.Response pause(String id, String comments) {
        Campaign campaign = findScoped(id);
        stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.PAUSED, comments);
        return campaignMapper.toResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignDto.Response resume(String id, String comments) {
        Campaign campaign = findScoped(id);
        Campaign.CampaignStatus target = campaign.getScheduledAt() != null && campaign.getScheduledAt().isAfter(java.time.Instant.now())
                ? Campaign.CampaignStatus.SCHEDULED
                : Campaign.CampaignStatus.SENDING;
        stateMachine.transitionCampaign(campaign, target, comments);
        return campaignMapper.toResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignDto.Response cancel(String id, String reason) {
        Campaign campaign = findScoped(id);
        stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.CANCELLED, reason);
        return campaignMapper.toResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignDto.Response duplicate(String id) {
        Campaign source = findScoped(id);
        Campaign clone = new Campaign();
        clone.setTenantId(source.getTenantId());
        clone.setWorkspaceId(source.getWorkspaceId());
        clone.setTeamId(source.getTeamId());
        clone.setOwnershipScope(source.getOwnershipScope());
        clone.setName(source.getName() + " (Copy)");
        clone.setSubject(source.getSubject());
        clone.setPreheader(source.getPreheader());
        clone.setContentId(source.getContentId());
        clone.setType(source.getType());
        clone.setSenderProfileId(source.getSenderProfileId());
        clone.setSenderName(source.getSenderName());
        clone.setSenderEmail(source.getSenderEmail());
        clone.setReplyToEmail(source.getReplyToEmail());
        clone.setBrandId(source.getBrandId());
        clone.setTrackingEnabled(source.getTrackingEnabled());
        clone.setComplianceEnabled(source.getComplianceEnabled());
        clone.setProviderId(source.getProviderId());
        clone.setSendingDomain(source.getSendingDomain());
        clone.setTimezone(source.getTimezone());
        clone.setQuietHoursStart(source.getQuietHoursStart());
        clone.setQuietHoursEnd(source.getQuietHoursEnd());
        clone.setSendWindowStart(source.getSendWindowStart());
        clone.setSendWindowEnd(source.getSendWindowEnd());
        clone.setFrequencyCap(source.getFrequencyCap());
        clone.setApprovalRequired(source.isApprovalRequired());
        clone.setExperimentConfig(source.getExperimentConfig());
        for (var audience : source.getAudiences()) {
            clone.addAudience(audience.getAudienceType(), audience.getAudienceId(), audience.getAction());
        }
        applyDefaults(clone);
        return campaignMapper.toResponse(campaignRepository.save(clone));
    }

    @Transactional
    public CampaignDto.Response schedule(String id, CampaignDto.ScheduleRequest request) {
        Campaign campaign = findScoped(id);
        if (request == null || request.getScheduledAt() == null) {
            throw new ValidationException("scheduledAt", "scheduledAt is required");
        }
        campaign.setScheduledAt(request.getScheduledAt());
        stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.SCHEDULED, "Campaign scheduled");
        return campaignMapper.toResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignDto.Response cloneCampaign(String id) {
        return duplicate(id);
    }

    private Campaign findScoped(String id) {
        return campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                        TenantContext.requireTenantId(),
                        TenantContext.requireWorkspaceId(),
                        id)
                .orElseThrow(() -> new NotFoundException("Campaign", id));
    }

    private void applyDefaults(Campaign campaign) {
        if (campaign.getOwnershipScope() == null || campaign.getOwnershipScope().isBlank()) {
            campaign.setOwnershipScope("WORKSPACE");
        }
        if (campaign.getTimezone() == null || campaign.getTimezone().isBlank()) {
            campaign.setTimezone("UTC");
        }
        if (campaign.getTrackingEnabled() == null) {
            campaign.setTrackingEnabled(Boolean.TRUE);
        }
        if (campaign.getComplianceEnabled() == null) {
            campaign.setComplianceEnabled(Boolean.TRUE);
        }
        if (campaign.getFrequencyCap() == null) {
            campaign.setFrequencyCap(0);
        }
        if (campaign.getExperimentConfig() == null || campaign.getExperimentConfig().isBlank()) {
            campaign.setExperimentConfig("{}");
        }
    }
}

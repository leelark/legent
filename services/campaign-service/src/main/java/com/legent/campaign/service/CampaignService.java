package com.legent.campaign.service;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.dto.CampaignDto;
import com.legent.campaign.mapper.CampaignMapper;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.common.exception.NotFoundException;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional(readOnly = true)
    public Page<CampaignDto.Response> search(String search, Pageable pageable) {
        String tenantId = TenantContext.getTenantId();
        Page<Campaign> campaigns;

        if (search == null || search.isBlank()) {
            campaigns = campaignRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable);
        } else {
            campaigns = campaignRepository.findByTenantIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
                    tenantId,
                    search.trim(),
                    pageable
            );
        }

        return campaigns
                .map(campaignMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CampaignDto.Response getById(String id) {
        return campaignRepository.findByTenantIdAndIdAndDeletedAtIsNull(TenantContext.getTenantId(), id)
                .map(campaignMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Campaign", id));
    }

    @Transactional
    public CampaignDto.Response create(CampaignDto.CreateRequest request) {
        String tenantId = TenantContext.getTenantId();
        Campaign campaign = campaignMapper.toEntity(request);
        campaign.setTenantId(tenantId);
        
        if (campaign.getAudiences() != null) {
            final Campaign finalCampaign = campaign;
            campaign.getAudiences().forEach(a -> {
                a.setTenantId(tenantId);
                a.setCampaign(finalCampaign);
            });
        }
        
        return campaignMapper.toResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignDto.Response update(String id, CampaignDto.UpdateRequest request) {
        Campaign campaign = campaignRepository.findByTenantIdAndIdAndDeletedAtIsNull(TenantContext.getTenantId(), id)
                .orElseThrow(() -> new NotFoundException("Campaign", id));
                
        // Only allow update if DRAFT
        if (campaign.getStatus() != Campaign.CampaignStatus.DRAFT) {
            throw new IllegalStateException("Cannot update campaign in status: " + campaign.getStatus());
        }

        campaignMapper.updateEntity(request, campaign);
        
        if (campaign.getAudiences() != null) {
            final Campaign finalCampaign = campaign;
            campaign.getAudiences().forEach(a -> {
                if (a.getTenantId() == null) a.setTenantId(finalCampaign.getTenantId());
                if (a.getCampaign() == null) a.setCampaign(finalCampaign);
            });
        }

        return campaignMapper.toResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public void delete(String id) {
        Campaign campaign = campaignRepository.findByTenantIdAndIdAndDeletedAtIsNull(TenantContext.getTenantId(), id)
                .orElseThrow(() -> new NotFoundException("Campaign", id));
        campaign.softDelete();
        campaignRepository.save(campaign);
    }
}

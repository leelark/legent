package com.legent.campaign.mapper;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.CampaignAudience;
import com.legent.campaign.dto.CampaignDto;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CampaignMapper {

    CampaignDto.Response toResponse(Campaign entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "audiences", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "contentId", source = "templateId")
    Campaign toEntity(CampaignDto.CreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "campaign", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    CampaignAudience toEntity(CampaignDto.AudienceRequest request);

    CampaignDto.AudienceResponse toResponse(CampaignAudience entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "audiences", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(CampaignDto.UpdateRequest request, @MappingTarget Campaign entity);
}

package com.legent.campaign.mapper;

import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.dto.SendJobDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SendJobMapper {

    SendJobDto.Response toJobResponse(SendJob entity);

    SendJobDto.BatchResponse toBatchResponse(SendBatch entity);
}

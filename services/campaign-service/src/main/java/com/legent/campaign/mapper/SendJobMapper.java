package com.legent.campaign.mapper;

import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.domain.SendJob;
import com.legent.campaign.dto.SendJobDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SendJobMapper {

    SendJobDto.Response toResponse(SendJob entity);

    SendJobDto.BatchResponse toResponse(SendBatch entity);
}

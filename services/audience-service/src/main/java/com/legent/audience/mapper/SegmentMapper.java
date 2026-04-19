package com.legent.audience.mapper;

import com.legent.audience.domain.Segment;
import com.legent.audience.dto.SegmentDto;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface SegmentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "status", constant = "DRAFT")
    @Mapping(target = "memberCount", constant = "0L")
    @Mapping(target = "lastEvaluatedAt", ignore = true)
    @Mapping(target = "evaluationDurationMs", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "segmentType", ignore = true)
    Segment toEntity(SegmentDto.CreateRequest request);

    @Mapping(target = "status", expression = "java(entity.getStatus().name())")
    @Mapping(target = "segmentType", expression = "java(entity.getSegmentType().name())")
    SegmentDto.Response toResponse(Segment entity);
}

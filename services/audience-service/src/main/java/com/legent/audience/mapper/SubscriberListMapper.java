package com.legent.audience.mapper;

import com.legent.audience.domain.SubscriberList;
import com.legent.audience.dto.SubscriberListDto;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface SubscriberListMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "memberCount", constant = "0L")
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "listType", ignore = true)
    SubscriberList toEntity(SubscriberListDto.CreateRequest request);

    @Mapping(target = "listType", expression = "java(entity.getListType().name())")
    @Mapping(target = "status", expression = "java(entity.getStatus().name())")
    SubscriberListDto.Response toResponse(SubscriberList entity);
}

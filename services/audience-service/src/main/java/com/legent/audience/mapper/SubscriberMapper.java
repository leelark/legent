package com.legent.audience.mapper;

import com.legent.audience.domain.Subscriber;
import com.legent.audience.dto.SubscriberDto;
import org.mapstruct.*;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SubscriberMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastActivityAt", ignore = true)
    @Mapping(target = "subscribedAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "unsubscribedAt", ignore = true)
    @Mapping(target = "bouncedAt", ignore = true)
    @Mapping(target = "emailFormat", constant = "HTML")
    Subscriber toEntity(SubscriberDto.CreateRequest request);

    @Mapping(target = "status", expression = "java(entity.getStatus().name())")
    SubscriberDto.Response toResponse(Subscriber entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "subscriberKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "bouncedAt", ignore = true)
    @Mapping(target = "emailFormat", ignore = true)
    @Mapping(target = "lastActivityAt", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "subscribedAt", ignore = true)
    @Mapping(target = "unsubscribedAt", ignore = true)
    void updateEntity(SubscriberDto.UpdateRequest request, @MappingTarget Subscriber entity);
}

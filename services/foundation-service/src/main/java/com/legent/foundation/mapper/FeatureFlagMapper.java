package com.legent.foundation.mapper;

import com.legent.foundation.domain.FeatureFlag;
import com.legent.foundation.dto.FeatureFlagDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for FeatureFlag entity ↔ DTOs.
 */
@Mapper(componentModel = "spring")
public interface FeatureFlagMapper {

    @Mapping(target = "scope", expression = "java(entity.getScope().name())")
    FeatureFlagDto.Response toResponse(FeatureFlag entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    FeatureFlag toEntity(FeatureFlagDto.CreateRequest request);
}

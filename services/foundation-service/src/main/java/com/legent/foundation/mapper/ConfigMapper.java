package com.legent.foundation.mapper;

import com.legent.foundation.domain.SystemConfig;
import com.legent.foundation.dto.ConfigDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for SystemConfig entity ↔ DTOs.
 */
@Mapper(componentModel = "spring")
public interface ConfigMapper {

    @Mapping(target = "valueType", expression = "java(entity.getValueType().name())")
    ConfigDto.Response toResponse(SystemConfig entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "encrypted", ignore = true)
    @Mapping(target = "system", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    SystemConfig toEntity(ConfigDto.CreateRequest request);
}

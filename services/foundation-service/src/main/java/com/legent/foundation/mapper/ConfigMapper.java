package com.legent.foundation.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.domain.SystemConfig;
import com.legent.foundation.dto.ConfigDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;
import java.util.List;

/**
 * MapStruct mapper for SystemConfig entity ↔ DTOs.
 */
@Mapper(componentModel = "spring")
public interface ConfigMapper {
    ObjectMapper MAPPER = new ObjectMapper();

    @Mapping(target = "valueType", expression = "java(entity.getValueType().name())")
    @Mapping(target = "scopeType", expression = "java(entity.getScopeType() != null ? entity.getScopeType().name() : null)")
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
    @Mapping(target = "configVersion", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "scopeType", ignore = true)
    @Mapping(target = "dependencyKeys", ignore = true)
    @Mapping(target = "validationSchema", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    SystemConfig toEntity(ConfigDto.CreateRequest request);

    default String map(List<String> value) {
        if (value == null) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    default String map(Map<String, Object> value) {
        if (value == null) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}

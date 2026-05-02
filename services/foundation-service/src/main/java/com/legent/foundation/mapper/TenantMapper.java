package com.legent.foundation.mapper;

import com.legent.foundation.domain.Tenant;
import com.legent.foundation.dto.TenantDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * MapStruct mapper for Tenant entity ↔ DTOs.
 */
@Mapper(componentModel = "spring")
public interface TenantMapper {

    @Mapping(target = "status", expression = "java(entity.getStatus().name())")
    TenantDto.Response toResponse(Tenant entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "suspendedAt", ignore = true)
    @Mapping(target = "suspensionReason", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    Tenant toEntity(TenantDto.CreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "suspendedAt", ignore = true)
    @Mapping(target = "suspensionReason", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    void updateEntity(TenantDto.UpdateRequest request, @MappingTarget Tenant entity);
}

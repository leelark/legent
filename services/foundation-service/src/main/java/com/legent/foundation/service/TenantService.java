package com.legent.foundation.service;

import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.foundation.domain.Tenant;
import com.legent.foundation.dto.TenantDto;
import com.legent.foundation.mapper.TenantMapper;
import com.legent.foundation.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for tenant lifecycle management.
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<TenantDto.Response> listTenants(Pageable pageable) {
        return tenantRepository.findAllActive(pageable).map(tenantMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TenantDto.Response getTenant(String id) {
        Tenant tenant = tenantRepository.findActiveById(id)
                .orElseThrow(() -> new NotFoundException("Tenant", id));
        return tenantMapper.toResponse(tenant);
    }

    @Transactional(readOnly = true)
    public TenantDto.Response getTenantBySlug(String slug) {
        Tenant tenant = tenantRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new NotFoundException("Tenant with slug " + slug));
        return tenantMapper.toResponse(tenant);
    }

    @Transactional
    public TenantDto.Response createTenant(TenantDto.CreateRequest request) {
        if (tenantRepository.existsBySlugAndDeletedAtIsNull(request.getSlug())) {
            throw new ConflictException("Tenant", "slug", request.getSlug());
        }

        Tenant entity = tenantMapper.toEntity(request);
        if (request.getPlan() == null) {
            entity.setPlan("STARTER");
        }

        Tenant saved = tenantRepository.save(entity);
        log.info("Tenant created: name={}, slug={}, id={}", saved.getName(), saved.getSlug(), saved.getId());

        auditService.log("TENANT_CREATE", "Tenant", saved.getId(),
                java.util.Map.of("name", saved.getName(), "slug", saved.getSlug()));

        return tenantMapper.toResponse(saved);
    }

    @Transactional
    public TenantDto.Response updateTenant(String id, TenantDto.UpdateRequest request) {
        Tenant existing = tenantRepository.findActiveById(id)
                .orElseThrow(() -> new NotFoundException("Tenant", id));

        tenantMapper.updateEntity(request, existing);

        if (request.getStatus() != null) {
            existing.setStatus(Tenant.TenantStatus.valueOf(request.getStatus()));
        }

        Tenant saved = tenantRepository.save(existing);
        log.info("Tenant updated: id={}", id);

        auditService.log("TENANT_UPDATE", "Tenant", id, java.util.Map.of("status", saved.getStatus().name()));

        return tenantMapper.toResponse(saved);
    }

    @Transactional
    public void deleteTenant(String id) {
        Tenant existing = tenantRepository.findActiveById(id)
                .orElseThrow(() -> new NotFoundException("Tenant", id));
        existing.softDelete();
        existing.setStatus(Tenant.TenantStatus.DEACTIVATED);
        tenantRepository.save(existing);
        log.info("Tenant soft-deleted: id={}", id);

        auditService.log("TENANT_DELETE", "Tenant", id, null);
    }
}

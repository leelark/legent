package com.legent.deliverability.controller;

import java.util.List;

import com.legent.common.dto.ApiResponse;
import com.legent.deliverability.domain.SenderDomain;
import com.legent.deliverability.repository.SenderDomainRepository;
import com.legent.deliverability.service.DomainVerificationService;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/deliverability/domains")
@RequiredArgsConstructor
public class DomainController {

    private final SenderDomainRepository domainRepository;
    private final DomainVerificationService domainVerificationService;

    @GetMapping
    public ApiResponse<List<SenderDomain>> listDomains() {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(domainRepository.findByTenantIdAndWorkspaceId(tenantId, workspaceId));
    }

    @PostMapping
    public ApiResponse<SenderDomain> registerDomain(
            @RequestBody SenderDomain request) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        request.setTenantId(tenantId);
        request.setWorkspaceId(workspaceId);
        request.setOwnershipScope("WORKSPACE");
        request.setId(java.util.UUID.randomUUID().toString());
        return ApiResponse.ok(domainRepository.save(request));
    }

    @PostMapping("/{domainId}/verify")
    public ApiResponse<SenderDomain> verifyDomain(
            @PathVariable String domainId) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        
        SenderDomain domain = domainRepository.findByTenantIdAndId(tenantId, domainId)
                .orElseThrow(() -> new com.legent.common.exception.NotFoundException("Domain", domainId));
        
        if (!tenantId.equals(domain.getTenantId()) || !workspaceId.equals(domain.getWorkspaceId())) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to verify this domain");
        }

        return ApiResponse.ok(domainVerificationService.verifyDomain(domainId));
    }
}

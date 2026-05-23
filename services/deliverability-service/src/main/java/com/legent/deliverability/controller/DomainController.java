package com.legent.deliverability.controller;

import java.util.List;
import java.util.Locale;

import com.legent.common.dto.ApiResponse;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.deliverability.domain.SenderDomain;
import com.legent.deliverability.repository.SenderDomainRepository;
import com.legent.deliverability.service.DomainVerificationService;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/deliverability/domains")
@RequiredArgsConstructor
public class DomainController {

    private final SenderDomainRepository domainRepository;
    private final DomainVerificationService domainVerificationService;

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('deliverability:read', principal.roles)")
    public ApiResponse<List<SenderDomain>> listDomains() {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(domainRepository.findByTenantIdAndWorkspaceId(tenantId, workspaceId));
    }

    @PostMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('deliverability:write', principal.roles)")
    public ApiResponse<SenderDomain> registerDomain(
            @RequestBody SenderDomain request) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        String domainName = normalizeDomain(request.getDomainName());
        if (domainName == null) {
            throw new IllegalArgumentException("domainName is required");
        }
        domainRepository.findByTenantIdAndWorkspaceIdAndDomainName(tenantId, workspaceId, domainName)
                .ifPresent(existing -> {
                    throw new ConflictException("SenderDomain", "domainName", domainName);
                });
        request.setTenantId(tenantId);
        request.setWorkspaceId(workspaceId);
        request.setOwnershipScope("WORKSPACE");
        request.setId(java.util.UUID.randomUUID().toString());
        request.setDomainName(domainName);
        domainVerificationService.issueChallenge(request);
        return ApiResponse.ok(domainRepository.save(request));
    }

    @PostMapping("/{domainId}/challenge")
    @PreAuthorize("@rbacEvaluator.hasPermission('deliverability:write', principal.roles)")
    public ApiResponse<SenderDomain> regenerateChallenge(@PathVariable String domainId) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        domainRepository.findByTenantIdAndWorkspaceIdAndId(tenantId, workspaceId, domainId)
                .orElseThrow(() -> new NotFoundException("Domain", domainId));
        return ApiResponse.ok(domainVerificationService.regenerateChallenge(domainId));
    }

    @PostMapping("/{domainId}/verify")
    @PreAuthorize("@rbacEvaluator.hasPermission('deliverability:write', principal.roles)")
    public ApiResponse<SenderDomain> verifyDomain(
            @PathVariable String domainId) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        domainRepository.findByTenantIdAndWorkspaceIdAndId(tenantId, workspaceId, domainId)
                .orElseThrow(() -> new NotFoundException("Domain", domainId));

        return ApiResponse.ok(domainVerificationService.verifyDomain(domainId));
    }

    private String normalizeDomain(String domainName) {
        if (domainName == null) {
            return null;
        }
        String normalized = domainName.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}

package com.legent.deliverability.controller;

import java.util.List;

import com.legent.common.dto.ApiResponse;
import com.legent.deliverability.domain.SenderDomain;
import com.legent.deliverability.repository.SenderDomainRepository;
import com.legent.deliverability.service.DomainVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/deliverability/domains")
@RequiredArgsConstructor
public class DomainController {

    private final SenderDomainRepository domainRepository;
    private final DomainVerificationService domainVerificationService;

    @GetMapping
    public ApiResponse<List<SenderDomain>> listDomains(@RequestHeader("X-Tenant-Id") String tenantId) {
        return ApiResponse.ok(domainRepository.findByTenantId(tenantId));
    }

    @PostMapping
    public ApiResponse<SenderDomain> registerDomain(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestBody SenderDomain request) {
        request.setTenantId(tenantId);
        request.setId(java.util.UUID.randomUUID().toString());
        return ApiResponse.ok(domainRepository.save(request));
    }

    @PostMapping("/{domainId}/verify")
    public ApiResponse<SenderDomain> verifyDomain(
            @RequestHeader("X-Tenant-Id") String tenantId, // Verification usually involves tenant validation internally
            @PathVariable String domainId) {
        return ApiResponse.ok(domainVerificationService.verifyDomain(domainId));
    }
}

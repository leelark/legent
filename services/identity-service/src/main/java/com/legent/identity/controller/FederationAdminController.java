package com.legent.identity.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.identity.dto.FederationDto;
import com.legent.identity.service.FederatedIdentityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/federation")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','PLATFORM_ADMIN','ORG_ADMIN','SECURITY_ADMIN')")
public class FederationAdminController {

    private final FederatedIdentityService federatedIdentityService;

    @PostMapping("/providers")
    public ApiResponse<Map<String, Object>> upsertProvider(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @Valid @RequestBody FederationDto.ProviderRequest request) {
        return ApiResponse.ok(federatedIdentityService.upsertProvider(tenantId, request));
    }

    @GetMapping("/providers")
    public ApiResponse<List<Map<String, Object>>> listProviders(@RequestHeader("X-Tenant-Id") String tenantId) {
        return ApiResponse.ok(federatedIdentityService.listProviders(tenantId));
    }

    @GetMapping("/providers/{providerKey}")
    public ApiResponse<Map<String, Object>> getProvider(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String providerKey) {
        return ApiResponse.ok(federatedIdentityService.getProvider(tenantId, providerKey));
    }

    @PostMapping("/scim-tokens")
    public ApiResponse<Map<String, Object>> createScimToken(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @Valid @RequestBody FederationDto.ScimTokenRequest request) {
        return ApiResponse.ok(federatedIdentityService.createScimToken(tenantId, request));
    }

    @GetMapping("/scim-tokens")
    public ApiResponse<List<Map<String, Object>>> listScimTokens(@RequestHeader("X-Tenant-Id") String tenantId) {
        return ApiResponse.ok(federatedIdentityService.listScimTokens(tenantId));
    }
}

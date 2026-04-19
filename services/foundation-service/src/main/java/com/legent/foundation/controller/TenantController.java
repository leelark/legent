package com.legent.foundation.controller;

import com.legent.common.constant.AppConstants;

import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import com.legent.foundation.dto.TenantDto;
import com.legent.foundation.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for tenant management.
 * Base path: /api/v1/tenants
 */
@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public PagedResponse<TenantDto.Response> listTenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TenantDto.Response> result = tenantService.listTenants(
                PageRequest.of(page, Math.min(size, AppConstants.MAX_PAGE_SIZE)));
        return PagedResponse.of(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<TenantDto.Response> getTenant(@PathVariable String id) {
        return ApiResponse.ok(tenantService.getTenant(id));
    }

    @GetMapping("/slug/{slug}")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<TenantDto.Response> getTenantBySlug(@PathVariable String slug) {
        return ApiResponse.ok(tenantService.getTenantBySlug(slug));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<TenantDto.Response> createTenant(
            @Valid @RequestBody TenantDto.CreateRequest request) {
        return ApiResponse.ok(tenantService.createTenant(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<TenantDto.Response> updateTenant(
            @PathVariable String id,
            @Valid @RequestBody TenantDto.UpdateRequest request) {
        return ApiResponse.ok(tenantService.updateTenant(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public void deleteTenant(@PathVariable String id) {
        tenantService.deleteTenant(id);
    }
}

package com.legent.foundation.controller;

import com.legent.common.constant.AppConstants;

import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import com.legent.foundation.dto.ConfigDto;
import com.legent.foundation.service.ConfigService;
import com.legent.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for system configuration management.
 * Base path: /api/v1/configs
 */
@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping("/resolve/{key}")
    @PreAuthorize("@rbacEvaluator.hasPermission('config:read', principal.roles)")
    public ApiResponse<ConfigDto.Response> resolveConfig(@PathVariable String key) {
        return ApiResponse.ok(configService.resolveConfig(key));
    }

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('config:read', principal.roles)")
    public PagedResponse<ConfigDto.Response> listConfigs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = TenantContext.getTenantId();
        Page<ConfigDto.Response> result = configService.listConfigs(
                tenantId, PageRequest.of(page, Math.min(size, AppConstants.MAX_PAGE_SIZE)));
        return PagedResponse.of(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('config:*', principal.roles)")
    public ApiResponse<ConfigDto.Response> createConfig(@Valid @RequestBody ConfigDto.CreateRequest request) {
        String tenantId = TenantContext.getTenantId();
        return ApiResponse.ok(configService.createConfig(tenantId, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('config:*', principal.roles)")
    public ApiResponse<ConfigDto.Response> updateConfig(
            @PathVariable String id,
            @Valid @RequestBody ConfigDto.UpdateRequest request) {
        return ApiResponse.ok(configService.updateConfig(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacEvaluator.hasPermission('config:*', principal.roles)")
    public void deleteConfig(@PathVariable String id) {
        configService.deleteConfig(id);
    }
}

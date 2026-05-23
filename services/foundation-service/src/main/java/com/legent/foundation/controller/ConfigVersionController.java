package com.legent.foundation.controller;

import java.util.List;
import java.util.Map;

import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import com.legent.foundation.domain.ConfigVersionHistory;
import com.legent.foundation.dto.ConfigDto;
import com.legent.foundation.service.ConfigVersioningService;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for configuration versioning and rollback operations.
 */
@RestController
@RequestMapping("/api/v1/configs")
@RequiredArgsConstructor
public class ConfigVersionController {

    private final ConfigVersioningService configVersioningService;

    @GetMapping("/{configKey}/versions")
    @PreAuthorize("@rbacEvaluator.hasPermission('config:read', principal.roles)")
    public ApiResponse<List<ConfigVersionHistory>> getConfigVersionHistory(@PathVariable String configKey) {
        String tenantId = requireTenantId();
        String workspaceId = TenantContext.getWorkspaceId();
        String environmentId = TenantContext.getEnvironmentId();
        return ApiResponse.ok(configVersioningService.getConfigVersionHistory(
                tenantId, workspaceId, environmentId, configKey));
    }

    @GetMapping("/{configKey}/versions/{version}")
    @PreAuthorize("@rbacEvaluator.hasPermission('config:read', principal.roles)")
    public ApiResponse<ConfigVersionHistory> getConfigVersion(
            @PathVariable String configKey,
            @PathVariable int version) {
        String tenantId = requireTenantId();
        String workspaceId = TenantContext.getWorkspaceId();
        String environmentId = TenantContext.getEnvironmentId();
        return ApiResponse.ok(configVersioningService.getConfigVersion(
                tenantId, workspaceId, environmentId, configKey, version));
    }

    @GetMapping("/versions")
    @PreAuthorize("@rbacEvaluator.hasPermission('config:read', principal.roles)")
    public PagedResponse<ConfigVersionHistory> getAllVersionHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = requireTenantId();
        String workspaceId = TenantContext.getWorkspaceId();
        String environmentId = TenantContext.getEnvironmentId();
        Page<ConfigVersionHistory> result = configVersioningService.getTenantVersionHistory(
                tenantId, workspaceId, environmentId, PageRequest.of(page, Math.min(size, 100)));
        return PagedResponse.of(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @PostMapping("/{configKey}/rollback/{version}")
    @PreAuthorize("@rbacEvaluator.hasPermission('config:*', principal.roles)")
    public ApiResponse<ConfigDto.Response> rollbackConfig(
            @PathVariable String configKey,
            @PathVariable int version) {
        String tenantId = requireTenantId();
        String workspaceId = TenantContext.getWorkspaceId();
        String environmentId = TenantContext.getEnvironmentId();
        return ApiResponse.ok(configVersioningService.rollbackConfig(
                tenantId, workspaceId, environmentId, configKey, version));
    }

    @GetMapping("/{configKey}/compare")
    @PreAuthorize("@rbacEvaluator.hasPermission('config:read', principal.roles)")
    public ApiResponse<Map<String, Object>> compareVersions(
            @PathVariable String configKey,
            @RequestParam int v1,
            @RequestParam int v2) {
        String tenantId = requireTenantId();
        String workspaceId = TenantContext.getWorkspaceId();
        String environmentId = TenantContext.getEnvironmentId();
        return ApiResponse.ok(configVersioningService.compareVersions(
                tenantId, workspaceId, environmentId, configKey, v1, v2));
    }

    private String requireTenantId() {
        return TenantContext.requireTenantId().trim();
    }
}

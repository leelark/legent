package com.legent.platform.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.platform.domain.TenantConfig;
import com.legent.platform.service.FoundationSettingsBridgeService;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/config")
@RequiredArgsConstructor
@PreAuthorize("@rbacEvaluator.hasPermission('config:*', principal.roles) or @rbacEvaluator.hasPermission('platform:*', principal.roles)")
public class PlatformConfigController {

    private final FoundationSettingsBridgeService bridgeService;

    @GetMapping
    public ApiResponse<TenantConfig> getConfig() {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(bridgeService.loadTenantConfig(tenantId, workspaceId));
    }

    @PostMapping
    public ApiResponse<TenantConfig> updateConfig(@RequestBody TenantConfig config) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        config.setTenantId(tenantId);
        return ApiResponse.ok(bridgeService.saveTenantConfig(tenantId, workspaceId, config));
    }
}

package com.legent.platform.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.platform.domain.TenantConfig;
import com.legent.platform.repository.TenantConfigRepository;
import com.legent.platform.service.FoundationSettingsBridgeService;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/config")
@RequiredArgsConstructor

public class PlatformConfigController {

    private final FoundationSettingsBridgeService bridgeService;
    private final TenantConfigRepository configRepository;

    @GetMapping
    public ApiResponse<TenantConfig> getConfig(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = "X-Workspace-Id", required = false) String workspaceIdHeader) {
        String tenantId = resolveTenantId(tenantIdHeader);
        String workspaceId = resolveWorkspaceId(workspaceIdHeader);
        try {
            return ApiResponse.ok(bridgeService.loadTenantConfig(tenantId, workspaceId));
        } catch (Exception ex) {
            TenantConfig fallback = configRepository.findById(tenantId).orElse(new TenantConfig());
            fallback.setTenantId(tenantId);
            return ApiResponse.ok(fallback);
        }
    }

    @PostMapping
    public ApiResponse<TenantConfig> updateConfig(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = "X-Workspace-Id", required = false) String workspaceIdHeader,
            @RequestBody TenantConfig config) {
        String tenantId = resolveTenantId(tenantIdHeader);
        String workspaceId = resolveWorkspaceId(workspaceIdHeader);
        config.setTenantId(tenantId);
        try {
            return ApiResponse.ok(bridgeService.saveTenantConfig(tenantId, workspaceId, config));
        } catch (Exception ex) {
            return ApiResponse.ok(configRepository.save(config));
        }
    }

    private String resolveTenantId(String tenantIdHeader) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
            return tenantIdHeader.trim();
        }
        throw new IllegalArgumentException("Tenant ID is required");
    }

    private String resolveWorkspaceId(String workspaceIdHeader) {
        String workspaceId = TenantContext.getWorkspaceId();
        if (workspaceId != null && !workspaceId.isBlank()) {
            return workspaceId;
        }
        if (workspaceIdHeader != null && !workspaceIdHeader.isBlank()) {
            return workspaceIdHeader.trim();
        }
        throw new IllegalArgumentException("Workspace ID is required");
    }
}

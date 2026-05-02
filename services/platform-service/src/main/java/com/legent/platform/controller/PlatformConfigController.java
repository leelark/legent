package com.legent.platform.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.platform.domain.TenantConfig;
import com.legent.platform.repository.TenantConfigRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/config")
@RequiredArgsConstructor

public class PlatformConfigController {

    private final TenantConfigRepository configRepository;

    @GetMapping
    public ApiResponse<TenantConfig> getConfig(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader) {
        String tenantId = resolveTenantId(tenantIdHeader);
        return ApiResponse.ok(configRepository.findById(tenantId).orElse(new TenantConfig()));
    }

    @PostMapping
    public ApiResponse<TenantConfig> updateConfig(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestBody TenantConfig config) {
        String tenantId = resolveTenantId(tenantIdHeader);
        config.setTenantId(tenantId);
        return ApiResponse.ok(configRepository.save(config));
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
}

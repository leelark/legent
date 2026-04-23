package com.legent.platform.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.platform.domain.TenantConfig;
import com.legent.platform.repository.TenantConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/config")
@RequiredArgsConstructor

public class PlatformConfigController {

    private final TenantConfigRepository configRepository;

    @GetMapping
    public ApiResponse<TenantConfig> getConfig(@RequestHeader("X-Tenant-Id") String tenantId) {
        return ApiResponse.ok(configRepository.findById(tenantId).orElse(new TenantConfig()));
    }

    @PostMapping
    public ApiResponse<TenantConfig> updateConfig(@RequestHeader("X-Tenant-Id") String tenantId, @RequestBody TenantConfig config) {
        config.setTenantId(tenantId);
        return ApiResponse.ok(configRepository.save(config));
    }
}

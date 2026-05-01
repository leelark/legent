package com.legent.foundation.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.common.exception.ValidationException;
import com.legent.foundation.domain.AdminConfig;
import com.legent.foundation.repository.AdminConfigRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/admin/configs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminConfigController {
    private final AdminConfigRepository repo;

    @GetMapping
    public ApiResponse<List<AdminConfig>> list() {
        String tenantId = TenantContext.getTenantId();
        List<AdminConfig> configs;
        if (tenantId != null && !tenantId.isBlank()) {
            configs = repo.findByTenantIdOrTenantIdIsNull(tenantId);
        } else {
            configs = repo.findAll();
        }
        return ApiResponse.ok(configs);
    }

    @PostMapping
    public ApiResponse<AdminConfig> save(@RequestBody AdminConfig config) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            config.setTenantId(tenantId);
        }
        if (config.getConfigKey() == null || config.getConfigKey().isBlank()) {
            throw new ValidationException("configKey", "Config key is required");
        }
        return ApiResponse.ok(repo.save(config));
    }
}

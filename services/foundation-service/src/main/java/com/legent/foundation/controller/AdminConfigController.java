package com.legent.foundation.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.common.exception.ValidationException;
import com.legent.foundation.dto.AdminSettingsDto;
import com.legent.foundation.service.AdminSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/admin/configs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')")
public class AdminConfigController {
    private final AdminSettingsService adminSettingsService;

    @GetMapping
    public ApiResponse<List<AdminSettingsDto.Entry>> list() {
        return ApiResponse.ok(adminSettingsService.listSettings(null, null, null));
    }

    @PostMapping
    public ApiResponse<AdminSettingsDto.Entry> save(@RequestBody AdminSettingsDto.ApplyRequest config) {
        if (config.getKey() == null || config.getKey().isBlank()) {
            throw new ValidationException("configKey", "Config key is required");
        }
        config.setScope(config.getScope() == null ? "TENANT" : config.getScope());
        return ApiResponse.ok(adminSettingsService.apply(config));
    }
}

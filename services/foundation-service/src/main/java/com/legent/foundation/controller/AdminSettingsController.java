package com.legent.foundation.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.foundation.domain.ConfigVersionHistory;
import com.legent.foundation.dto.AdminSettingsDto;
import com.legent.foundation.service.AdminSettingsService;
import com.legent.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')")
public class AdminSettingsController {

    private final AdminSettingsService adminSettingsService;

    @GetMapping
    public ApiResponse<List<AdminSettingsDto.Entry>> list(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String scope) {
        return ApiResponse.ok(adminSettingsService.listSettings(module, category, scope));
    }

    @PostMapping("/validate")
    public ApiResponse<AdminSettingsDto.ValidateResponse> validate(
            @Valid @RequestBody AdminSettingsDto.ApplyRequest request) {
        enforceScopedWorkspace(request.getScope(), request.getWorkspaceId(), request.getEnvironmentId());
        return ApiResponse.ok(adminSettingsService.validate(request));
    }

    @PostMapping("/apply")
    public ApiResponse<AdminSettingsDto.Entry> apply(
            @Valid @RequestBody AdminSettingsDto.ApplyRequest request) {
        enforceScopedWorkspace(request.getScope(), request.getWorkspaceId(), request.getEnvironmentId());
        return ApiResponse.ok(adminSettingsService.apply(request));
    }

    @PostMapping("/reset")
    public ApiResponse<AdminSettingsDto.Entry> reset(
            @Valid @RequestBody AdminSettingsDto.ResetRequest request) {
        enforceScopedWorkspace(request.getScope(), request.getWorkspaceId(), request.getEnvironmentId());
        return ApiResponse.ok(adminSettingsService.reset(request));
    }

    @GetMapping("/impact")
    public ApiResponse<AdminSettingsDto.ImpactResponse> impact(
            @RequestParam String key,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String environmentId) {
        enforceScopedWorkspace(scope, workspaceId, environmentId);
        AdminSettingsDto.ApplyRequest request = new AdminSettingsDto.ApplyRequest();
        request.setKey(key);
        request.setModule(module);
        request.setScope(scope);
        request.setWorkspaceId(workspaceId);
        request.setEnvironmentId(environmentId);
        request.setValue("preview");
        return ApiResponse.ok(adminSettingsService.impact(request));
    }

    @GetMapping("/history")
    public ApiResponse<List<ConfigVersionHistory>> history(
            @RequestParam(required = false) String key) {
        return ApiResponse.ok(adminSettingsService.history(key));
    }

    @PostMapping("/rollback")
    public ApiResponse<AdminSettingsDto.Entry> rollback(
            @RequestParam String key,
            @RequestParam int version) {
        return ApiResponse.ok(adminSettingsService.rollback(key, version));
    }

    private void enforceScopedWorkspace(String scope, String workspaceId, String environmentId) {
        String effectiveScope = scope == null ? null : scope.trim().toUpperCase();
        String effectiveWorkspace = workspaceId != null ? workspaceId : TenantContext.getWorkspaceId();
        String effectiveEnvironment = environmentId != null ? environmentId : TenantContext.getEnvironmentId();

        if ("WORKSPACE".equals(effectiveScope) && (effectiveWorkspace == null || effectiveWorkspace.isBlank())) {
            throw new IllegalArgumentException("X-Workspace-Id is required for WORKSPACE scope");
        }
        if ("ENVIRONMENT".equals(effectiveScope) && (effectiveWorkspace == null || effectiveWorkspace.isBlank())) {
            throw new IllegalArgumentException("X-Workspace-Id is required for ENVIRONMENT scope");
        }
        if ("ENVIRONMENT".equals(effectiveScope) && (effectiveEnvironment == null || effectiveEnvironment.isBlank())) {
            throw new IllegalArgumentException("X-Environment-Id is required for ENVIRONMENT scope");
        }
    }
}

package com.legent.foundation.controller;

import com.legent.common.constant.AppConstants;

import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import com.legent.foundation.dto.FeatureFlagDto;
import com.legent.foundation.service.FeatureFlagService;
import com.legent.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for feature flag management and evaluation.
 * Base path: /api/v1/feature-flags
 */
@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/feature-flags")
@RequiredArgsConstructor
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    @GetMapping("/evaluate/{key}")
    @PreAuthorize("@rbacEvaluator.hasPermission('feature:read', principal.roles)")
    public ApiResponse<FeatureFlagDto.EvaluationResult> evaluate(@PathVariable String key) {
        return ApiResponse.ok(featureFlagService.evaluate(key));
    }

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('feature:read', principal.roles)")
    public PagedResponse<FeatureFlagDto.Response> listFlags(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = TenantContext.getTenantId();
        Page<FeatureFlagDto.Response> result = featureFlagService.listFlags(
                tenantId, PageRequest.of(page, Math.min(size, AppConstants.MAX_PAGE_SIZE)));
        return PagedResponse.of(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<FeatureFlagDto.Response> getFlag(@PathVariable String id) {
        return ApiResponse.ok(featureFlagService.getFlag(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('feature:*', principal.roles)")
    public ApiResponse<FeatureFlagDto.Response> createFlag(
            @Valid @RequestBody FeatureFlagDto.CreateRequest request) {
        String tenantId = TenantContext.getTenantId();
        return ApiResponse.ok(featureFlagService.createFlag(tenantId, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('feature:*', principal.roles)")
    public ApiResponse<FeatureFlagDto.Response> updateFlag(
            @PathVariable String id,
            @Valid @RequestBody FeatureFlagDto.UpdateRequest request) {
        return ApiResponse.ok(featureFlagService.updateFlag(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacEvaluator.hasPermission('feature:*', principal.roles)")
    public void deleteFlag(@PathVariable String id) {
        featureFlagService.deleteFlag(id);
    }
}

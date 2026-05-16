package com.legent.tracking.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.security.TenantContext;
import com.legent.tracking.service.ClickHouseRollupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics/bi")
@RequiredArgsConstructor
public class BiAnalyticsController {

    private final ClickHouseRollupService clickHouseRollupService;

    @GetMapping("/datasets")
    @PreAuthorize("@rbacEvaluator.hasPermission('tracking:read', principal.roles) or @rbacEvaluator.hasPermission('analytics:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> datasets() {
        return ApiResponse.ok(clickHouseRollupService.datasets());
    }

    @PostMapping("/rollups/ensure")
    @PreAuthorize("@rbacEvaluator.hasPermission('tracking:write', principal.roles) or @rbacEvaluator.hasPermission('analytics:write', principal.roles)")
    public ApiResponse<Map<String, Object>> ensureRollups() {
        return ApiResponse.ok(clickHouseRollupService.ensureRollupSchema());
    }

    @PostMapping("/rollups/refresh")
    @PreAuthorize("@rbacEvaluator.hasPermission('tracking:write', principal.roles) or @rbacEvaluator.hasPermission('analytics:write', principal.roles)")
    public ApiResponse<Map<String, Object>> refreshRollups(@RequestBody(required = false) RefreshRequest request) {
        RefreshRequest safe = request == null ? new RefreshRequest(null, null) : request;
        return ApiResponse.ok(clickHouseRollupService.refreshCampaignDayRollups(
                TenantContext.requireTenantId(),
                TenantContext.requireWorkspaceId(),
                safe.from(),
                safe.to()));
    }

    @GetMapping("/campaign-performance")
    @PreAuthorize("@rbacEvaluator.hasPermission('tracking:read', principal.roles) or @rbacEvaluator.hasPermission('analytics:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> campaignPerformance(@RequestParam(required = false) Instant from,
                                                                      @RequestParam(required = false) Instant to,
                                                                      @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(clickHouseRollupService.campaignPerformance(
                TenantContext.requireTenantId(),
                TenantContext.requireWorkspaceId(),
                from,
                to,
                limit));
    }

    public record RefreshRequest(Instant from, Instant to) {}
}

package com.legent.tracking.controller;

import java.util.List;
import java.util.Map;

import com.legent.common.dto.ApiResponse;
import com.legent.tracking.domain.CampaignSummary;
import com.legent.tracking.repository.CampaignSummaryRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final CampaignSummaryRepository campaignSummaryRepository;
    private final com.legent.tracking.service.AnalyticsService analyticsService;

    @GetMapping("/campaigns")
    public ApiResponse<List<CampaignSummary>> getAllCampaignSummaries() {
        String tenantId = TenantContext.getTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(campaignSummaryRepository.findAllByTenantIdAndWorkspaceId(tenantId, workspaceId));
    }

    @GetMapping("/campaigns/{id}")
    public ApiResponse<CampaignSummary> getCampaignSummary(@PathVariable String id) {
        String tenantId = TenantContext.getTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        CampaignSummary summary = campaignSummaryRepository.findByTenantIdAndWorkspaceIdAndCampaignId(tenantId, workspaceId, id)
                .orElse(new CampaignSummary());
        return ApiResponse.ok(summary);
    }

    @GetMapping("/events/counts")
    public ApiResponse<List<Map<String, Object>>> getEventCounts() {
        String tenantId = TenantContext.getTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(analyticsService.getEventCounts(tenantId, workspaceId));
    }

    @GetMapping("/events/timeline")
    public ApiResponse<List<Map<String, Object>>> getEventTimeline(@RequestParam String eventType) {
        String tenantId = TenantContext.getTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(analyticsService.getEventTimeline(tenantId, workspaceId, eventType));
    }
}

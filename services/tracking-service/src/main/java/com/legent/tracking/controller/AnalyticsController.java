package com.legent.tracking.controller;

import java.util.List;

import com.legent.common.dto.ApiResponse;
import com.legent.tracking.domain.CampaignSummary;
import com.legent.tracking.repository.CampaignSummaryRepository;
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
        return ApiResponse.ok(campaignSummaryRepository.findAll());
    }

    @GetMapping("/campaigns/{id}")
    public ApiResponse<CampaignSummary> getCampaignSummary(
            @RequestHeader("X-Tenant-Id") String tenantId, 
            @PathVariable String id) {
        
        CampaignSummary summary = campaignSummaryRepository.findByTenantIdAndCampaignId(tenantId, id)
                .orElse(new CampaignSummary()); // Return zeros if none
        return ApiResponse.ok(summary);
    }

    @GetMapping("/events/counts")
    public ApiResponse<List<java.util.Map<String, Object>>> getEventCounts(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ApiResponse.ok(analyticsService.getEventCounts(tenantId));
    }

    @GetMapping("/events/timeline")
    public ApiResponse<List<java.util.Map<String, Object>>> getEventTimeline(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam String eventType) {
        return ApiResponse.ok(analyticsService.getEventTimeline(tenantId, eventType));
    }
}

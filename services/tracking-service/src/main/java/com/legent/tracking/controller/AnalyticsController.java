package com.legent.tracking.controller;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.dto.ApiResponse;
import com.legent.tracking.domain.CampaignSummary;
import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.repository.CampaignSummaryRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CampaignSummaryRepository campaignSummaryRepository;
    private final com.legent.tracking.service.AnalyticsService analyticsService;

    @GetMapping("/campaigns")
    @PreAuthorize("@rbacEvaluator.hasPermission('tracking:read', principal.roles) or @rbacEvaluator.hasPermission('analytics:read', principal.roles)")
    public ApiResponse<List<CampaignSummary>> getAllCampaignSummaries() {
        String tenantId = TenantContext.getTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(campaignSummaryRepository.findAllByTenantIdAndWorkspaceId(tenantId, workspaceId));
    }

    @GetMapping("/campaigns/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('tracking:read', principal.roles) or @rbacEvaluator.hasPermission('analytics:read', principal.roles)")
    public ApiResponse<CampaignSummary> getCampaignSummary(@PathVariable String id) {
        String tenantId = TenantContext.getTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        CampaignSummary summary = campaignSummaryRepository.findByTenantIdAndWorkspaceIdAndCampaignId(tenantId, workspaceId, id)
                .orElse(new CampaignSummary());
        return ApiResponse.ok(summary);
    }

    @GetMapping("/events/counts")
    @PreAuthorize("@rbacEvaluator.hasPermission('tracking:read', principal.roles) or @rbacEvaluator.hasPermission('analytics:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> getEventCounts() {
        String tenantId = TenantContext.getTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(analyticsService.getEventCounts(tenantId, workspaceId));
    }

    @GetMapping("/events/timeline")
    @PreAuthorize("@rbacEvaluator.hasPermission('tracking:read', principal.roles) or @rbacEvaluator.hasPermission('analytics:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> getEventTimeline(@RequestParam String eventType) {
        String tenantId = TenantContext.getTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(analyticsService.getEventTimeline(tenantId, workspaceId, eventType));
    }

    @GetMapping("/campaigns/{id}/experiments/{experimentId}")
    @PreAuthorize("@rbacEvaluator.hasPermission('tracking:read', principal.roles) or @rbacEvaluator.hasPermission('analytics:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> getExperimentMetrics(@PathVariable String id,
                                                                       @PathVariable String experimentId) {
        String tenantId = TenantContext.getTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(analyticsService.getExperimentMetrics(tenantId, workspaceId, id, experimentId));
    }

    @PostMapping("/events/export")
    @PreAuthorize("@rbacEvaluator.hasPermission('tracking:read', principal.roles) or @rbacEvaluator.hasPermission('analytics:read', principal.roles)")
    public ApiResponse<TrackingDto.EventExportResponse> exportEvents(@RequestBody(required = false) TrackingDto.EventExportRequest request) {
        TrackingDto.EventExportRequest safeRequest = request == null ? new TrackingDto.EventExportRequest() : request;
        String tenantId = TenantContext.getTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        int limit = safeRequest.getLimit() == null ? 1000 : safeRequest.getLimit();
        List<Map<String, Object>> rows = analyticsService.exportEvents(
                tenantId,
                workspaceId,
                safeRequest.getCampaignId(),
                safeRequest.getEventTypes(),
                safeRequest.getStartAt(),
                safeRequest.getEndAt(),
                limit);
        String format = safeRequest.getFormat() == null ? "CSV" : safeRequest.getFormat().toUpperCase();
        return ApiResponse.ok(TrackingDto.EventExportResponse.builder()
                .format(format)
                .rowCount(rows.size())
                .content("JSON".equals(format) ? writeJson(rows) : analyticsService.toCsv(rows))
                .metadata(Map.of("cappedLimit", Math.max(1, Math.min(limit, 10_000))))
                .build());
    }

    @GetMapping("/rollups")
    @PreAuthorize("@rbacEvaluator.hasPermission('tracking:read', principal.roles) or @rbacEvaluator.hasPermission('analytics:read', principal.roles)")
    public ApiResponse<TrackingDto.RollupResponse> getRollups(@RequestParam(required = false) String campaignId,
                                                              @RequestParam(defaultValue = "hour") String grain) {
        String tenantId = TenantContext.getTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(TrackingDto.RollupResponse.builder()
                .campaignId(campaignId)
                .grain("day".equalsIgnoreCase(grain) ? "day" : "hour")
                .rows(analyticsService.getRollups(tenantId, workspaceId, campaignId, grain))
                .build());
    }

    @GetMapping("/taxonomy")
    @PreAuthorize("@rbacEvaluator.hasPermission('tracking:read', principal.roles) or @rbacEvaluator.hasPermission('analytics:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> taxonomy() {
        return ApiResponse.ok(analyticsService.taxonomy());
    }

    @GetMapping("/campaigns/{id}/reconciliation")
    @PreAuthorize("@rbacEvaluator.hasPermission('tracking:read', principal.roles) or @rbacEvaluator.hasPermission('analytics:read', principal.roles)")
    public ApiResponse<TrackingDto.ReconciliationResponse> reconcileCampaign(@PathVariable String id) {
        String tenantId = TenantContext.getTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        CampaignSummary summary = campaignSummaryRepository.findByTenantIdAndWorkspaceIdAndCampaignId(tenantId, workspaceId, id)
                .orElse(new CampaignSummary());
        Map<String, Object> summaryCounts = new LinkedHashMap<>();
        summaryCounts.put("SEND", summary.getTotalSends());
        summaryCounts.put("OPEN", summary.getTotalOpens());
        summaryCounts.put("CLICK", summary.getTotalClicks());
        summaryCounts.put("CONVERSION", summary.getTotalConversions());
        summaryCounts.put("BOUNCE", summary.getTotalBounces());
        Map<String, Object> rawCounts = analyticsService.rawCountsForCampaign(tenantId, workspaceId, id);
        List<String> mismatches = summaryCounts.entrySet().stream()
                .filter(entry -> !String.valueOf(entry.getValue() == null ? 0L : entry.getValue())
                        .equals(String.valueOf(rawCounts.getOrDefault(entry.getKey(), 0L))))
                .map(entry -> entry.getKey() + " summary=" + entry.getValue() + " raw=" + rawCounts.getOrDefault(entry.getKey(), 0L))
                .toList();
        return ApiResponse.ok(TrackingDto.ReconciliationResponse.builder()
                .campaignId(id)
                .summaryCounts(summaryCounts)
                .rawEventCounts(rawCounts)
                .mismatches(mismatches)
                .reconciled(mismatches.isEmpty())
                .build());
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ignored) {
            return "[]";
        }
    }
}

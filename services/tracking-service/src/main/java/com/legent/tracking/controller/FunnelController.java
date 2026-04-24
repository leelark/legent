package com.legent.tracking.controller;

import com.legent.tracking.service.FunnelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics/funnel")
@RequiredArgsConstructor
public class FunnelController {
    private final FunnelService funnelService;

    @GetMapping
    public List<Map<String, Object>> getFunnel(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam String campaignId) {
        return funnelService.getFunnel(tenantId, campaignId);
    }
}

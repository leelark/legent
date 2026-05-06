package com.legent.tracking.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.security.TenantContext;
import com.legent.tracking.service.FunnelService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics/funnel")
@RequiredArgsConstructor
@Validated
public class FunnelController {
    private final FunnelService funnelService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getFunnel(@RequestParam @NotBlank String campaignId) {
        String tenantId = TenantContext.getTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(funnelService.getFunnel(tenantId, workspaceId, campaignId));
    }
}

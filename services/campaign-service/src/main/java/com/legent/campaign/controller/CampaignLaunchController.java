package com.legent.campaign.controller;

import com.legent.campaign.dto.CampaignLaunchDto;
import com.legent.campaign.service.CampaignLaunchOrchestrationService;
import com.legent.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignLaunchController {

    private final CampaignLaunchOrchestrationService launchService;

    @PostMapping("/launch-plans/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST')")
    public ApiResponse<CampaignLaunchDto.LaunchPlanResponse> preview(
            @Valid @RequestBody CampaignLaunchDto.LaunchPlanRequest request) {
        return ApiResponse.ok(launchService.preview(request));
    }

    @PostMapping("/launch-plans/execute")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<CampaignLaunchDto.LaunchPlanResponse> execute(
            @Valid @RequestBody CampaignLaunchDto.LaunchPlanRequest request) {
        return ApiResponse.ok(launchService.execute(request));
    }

    @GetMapping("/{campaignId}/launch-readiness")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')")
    public ApiResponse<CampaignLaunchDto.LaunchPlanResponse> readiness(@PathVariable String campaignId) {
        return ApiResponse.ok(launchService.readiness(campaignId));
    }
}

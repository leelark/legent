package com.legent.campaign.controller;

import java.util.List;

import com.legent.campaign.dto.CampaignEngineDto;
import com.legent.campaign.service.CampaignEngineService;
import com.legent.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CampaignEngineController {

    private final CampaignEngineService campaignEngineService;

    @GetMapping("/campaigns/{id}/experiments")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')")
    public ApiResponse<List<CampaignEngineDto.ExperimentResponse>> listExperiments(@PathVariable String id) {
        return ApiResponse.ok(campaignEngineService.listExperiments(id));
    }

    @PostMapping("/campaigns/{id}/experiments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<CampaignEngineDto.ExperimentResponse> createExperiment(
            @PathVariable String id,
            @Valid @RequestBody CampaignEngineDto.ExperimentRequest request) {
        return ApiResponse.ok(campaignEngineService.createExperiment(id, request));
    }

    @PutMapping("/campaigns/{id}/experiments/{experimentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<CampaignEngineDto.ExperimentResponse> updateExperiment(
            @PathVariable String id,
            @PathVariable String experimentId,
            @Valid @RequestBody CampaignEngineDto.ExperimentRequest request) {
        return ApiResponse.ok(campaignEngineService.updateExperiment(id, experimentId, request));
    }

    @DeleteMapping("/campaigns/{id}/experiments/{experimentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public void deleteExperiment(@PathVariable String id, @PathVariable String experimentId) {
        campaignEngineService.deleteExperiment(id, experimentId);
    }

    @PostMapping("/campaigns/{id}/experiments/{experimentId}/promote-winner")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<CampaignEngineDto.ExperimentResponse> promoteWinner(
            @PathVariable String id,
            @PathVariable String experimentId) {
        return ApiResponse.ok(campaignEngineService.promoteWinner(id, experimentId));
    }

    @GetMapping("/campaigns/{id}/experiments/{experimentId}/metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')")
    public ApiResponse<List<CampaignEngineDto.VariantMetricsResponse>> getExperimentMetrics(
            @PathVariable String id,
            @PathVariable String experimentId) {
        return ApiResponse.ok(campaignEngineService.getExperimentMetrics(id, experimentId));
    }

    @GetMapping("/campaigns/{id}/experiments/{experimentId}/analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')")
    public ApiResponse<CampaignEngineDto.ExperimentAnalysisResponse> analyzeExperiment(
            @PathVariable String id,
            @PathVariable String experimentId) {
        return ApiResponse.ok(campaignEngineService.analyzeExperiment(id, experimentId));
    }

    @GetMapping("/campaigns/{id}/budget")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')")
    public ApiResponse<CampaignEngineDto.BudgetResponse> getBudget(@PathVariable String id) {
        return ApiResponse.ok(campaignEngineService.getBudget(id));
    }

    @PutMapping("/campaigns/{id}/budget")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<CampaignEngineDto.BudgetResponse> updateBudget(
            @PathVariable String id,
            @Valid @RequestBody CampaignEngineDto.BudgetRequest request) {
        return ApiResponse.ok(campaignEngineService.updateBudget(id, request));
    }

    @GetMapping("/campaigns/{id}/frequency-policy")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')")
    public ApiResponse<CampaignEngineDto.FrequencyPolicyResponse> getFrequencyPolicy(@PathVariable String id) {
        return ApiResponse.ok(campaignEngineService.getFrequencyPolicy(id));
    }

    @PutMapping("/campaigns/{id}/frequency-policy")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<CampaignEngineDto.FrequencyPolicyResponse> updateFrequencyPolicy(
            @PathVariable String id,
            @Valid @RequestBody CampaignEngineDto.FrequencyPolicyRequest request) {
        return ApiResponse.ok(campaignEngineService.updateFrequencyPolicy(id, request));
    }

    @PostMapping("/campaigns/{id}/send/preflight")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST')")
    public ApiResponse<CampaignEngineDto.SendPreflightReport> preflight(@PathVariable String id) {
        return ApiResponse.ok(campaignEngineService.preflight(id));
    }

    @PostMapping("/campaigns/{id}/send/resend-plans")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<CampaignEngineDto.ResendPlanResponse> createResendPlan(
            @PathVariable String id,
            @Valid @RequestBody CampaignEngineDto.ResendPlanRequest request) {
        return ApiResponse.ok(campaignEngineService.createResendPlan(id, request));
    }

    @GetMapping("/send-jobs/{jobId}/dead-letters")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')")
    public ApiResponse<List<CampaignEngineDto.DeadLetterResponse>> listDeadLetters(@PathVariable String jobId) {
        return ApiResponse.ok(campaignEngineService.listDeadLetters(jobId));
    }

    @PostMapping("/send-jobs/{jobId}/dead-letters/{deadLetterId}/replay")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<CampaignEngineDto.DeadLetterResponse> replayDeadLetter(
            @PathVariable String jobId,
            @PathVariable String deadLetterId) {
        return ApiResponse.ok(campaignEngineService.replayDeadLetter(jobId, deadLetterId));
    }
}

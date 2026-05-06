package com.legent.campaign.controller;

import com.legent.campaign.dto.SendJobDto;
import com.legent.campaign.service.OrchestrationService;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SendJobController {

    private final OrchestrationService orchestrationService;

    @PostMapping("/campaigns/{id}/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<SendJobDto.Response> triggerSend(@PathVariable String id,
                                                        @RequestBody(required = false) SendJobDto.TriggerRequest request) {
        if (request == null) request = new SendJobDto.TriggerRequest();
        return ApiResponse.ok(orchestrationService.triggerSend(id, request));
    }

    @GetMapping("/campaigns/{id}/jobs")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')")
    public PagedResponse<SendJobDto.Response> getJobsForCampaign(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SendJobDto.Response> results = orchestrationService.getJobsForCampaign(id, PageRequest.of(page, size));
        return PagedResponse.of(results.getContent(), page, size, results.getTotalElements(), results.getTotalPages());
    }

    @GetMapping("/send-jobs/{jobId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')")
    public ApiResponse<SendJobDto.Response> getJobStatus(@PathVariable String jobId) {
        return ApiResponse.ok(orchestrationService.getJobStatus(jobId));
    }

    @PostMapping("/campaigns/{id}/send/pause")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<SendJobDto.Response> pause(@PathVariable String id,
                                                  @RequestBody(required = false) com.legent.campaign.dto.CampaignDto.LifecycleActionRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ApiResponse.ok(orchestrationService.pauseCampaignSend(id, reason));
    }

    @PostMapping("/campaigns/{id}/send/resume")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<SendJobDto.Response> resume(@PathVariable String id,
                                                   @RequestBody(required = false) com.legent.campaign.dto.CampaignDto.LifecycleActionRequest request) {
        String comments = request != null ? request.getComments() : null;
        return ApiResponse.ok(orchestrationService.resumeCampaignSend(id, comments));
    }

    @PostMapping("/campaigns/{id}/send/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<SendJobDto.Response> cancel(@PathVariable String id,
                                                   @RequestBody(required = false) com.legent.campaign.dto.CampaignDto.LifecycleActionRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ApiResponse.ok(orchestrationService.cancelCampaignSend(id, reason));
    }

    @PostMapping("/send-jobs/{jobId}/retry")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<SendJobDto.Response> retry(@PathVariable String jobId,
                                                  @RequestBody(required = false) com.legent.campaign.dto.CampaignDto.LifecycleActionRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ApiResponse.ok(orchestrationService.retryJob(jobId, reason));
    }

    @PostMapping("/campaigns/{id}/send/resend")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<SendJobDto.Response> resend(@PathVariable String id,
                                                   @RequestBody(required = false) com.legent.campaign.dto.CampaignDto.LifecycleActionRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ApiResponse.ok(orchestrationService.resendCampaign(id, reason));
    }

    @PostMapping("/campaigns/{id}/trigger-launch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<SendJobDto.Response> triggerLaunch(@PathVariable String id,
                                                          @RequestBody(required = false) com.legent.campaign.dto.CampaignDto.TriggerLaunchRequest request) {
        com.legent.campaign.dto.CampaignDto.TriggerLaunchRequest payload =
                request != null ? request : new com.legent.campaign.dto.CampaignDto.TriggerLaunchRequest();
        return ApiResponse.ok(orchestrationService.triggerFromAutomation(id, payload));
    }
}

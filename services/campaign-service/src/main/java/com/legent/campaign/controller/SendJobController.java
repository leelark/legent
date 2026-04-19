package com.legent.campaign.controller;

import com.legent.campaign.dto.SendJobDto;
import com.legent.campaign.service.OrchestrationService;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SendJobController {

    private final OrchestrationService orchestrationService;

    @PostMapping("/campaigns/{id}/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<SendJobDto.Response> triggerSend(@PathVariable String id,
                                                        @RequestBody(required = false) SendJobDto.TriggerRequest request) {
        if (request == null) request = new SendJobDto.TriggerRequest();
        return ApiResponse.ok(orchestrationService.triggerSend(id, request));
    }

    @GetMapping("/campaigns/{id}/jobs")
    public PagedResponse<SendJobDto.Response> getJobsForCampaign(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SendJobDto.Response> results = orchestrationService.getJobsForCampaign(id, PageRequest.of(page, size));
        return PagedResponse.of(results.getContent(), page, size, results.getTotalElements(), results.getTotalPages());
    }

    @GetMapping("/send-jobs/{jobId}")
    public ApiResponse<SendJobDto.Response> getJobStatus(@PathVariable String jobId) {
        return ApiResponse.ok(orchestrationService.getJobStatus(jobId));
    }
}

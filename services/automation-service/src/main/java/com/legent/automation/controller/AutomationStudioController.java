package com.legent.automation.controller;

import com.legent.automation.dto.AutomationStudioDto;
import com.legent.automation.service.AutomationStudioService;
import com.legent.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/automation-studio/activities")
@RequiredArgsConstructor
public class AutomationStudioController {

    private final AutomationStudioService automationStudioService;

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:read', principal.roles)")
    public ApiResponse<List<AutomationStudioDto.ActivityResponse>> listActivities() {
        return ApiResponse.ok(automationStudioService.listActivities());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:write', principal.roles)")
    public ApiResponse<AutomationStudioDto.ActivityResponse> createActivity(
            @Valid @RequestBody AutomationStudioDto.ActivityRequest request) {
        return ApiResponse.ok(automationStudioService.createActivity(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:read', principal.roles)")
    public ApiResponse<AutomationStudioDto.ActivityResponse> getActivity(@PathVariable String id) {
        return ApiResponse.ok(automationStudioService.getActivity(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:write', principal.roles)")
    public ApiResponse<AutomationStudioDto.ActivityResponse> updateActivity(
            @PathVariable String id,
            @Valid @RequestBody AutomationStudioDto.ActivityRequest request) {
        return ApiResponse.ok(automationStudioService.updateActivity(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:write', principal.roles)")
    public void archiveActivity(@PathVariable String id) {
        automationStudioService.archiveActivity(id);
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:write', principal.roles)")
    public ApiResponse<AutomationStudioDto.VerificationResponse> verifyActivity(@PathVariable String id) {
        return ApiResponse.ok(automationStudioService.verifyActivity(id));
    }

    @PostMapping("/{id}/runs")
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:write', principal.roles)")
    public ApiResponse<AutomationStudioDto.RunResponse> runActivity(
            @PathVariable String id,
            @RequestBody(required = false) AutomationStudioDto.RunRequest request) {
        return ApiResponse.ok(automationStudioService.runActivity(id, request));
    }

    @GetMapping("/{id}/runs")
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:read', principal.roles)")
    public ApiResponse<List<AutomationStudioDto.RunResponse>> listRuns(@PathVariable String id) {
        return ApiResponse.ok(automationStudioService.listRuns(id));
    }
}

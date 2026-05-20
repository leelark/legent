package com.legent.automation.controller;

import com.legent.automation.dto.AutomationStudioDto;
import com.legent.automation.service.AutomationArtifactService;
import com.legent.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/automation-studio/artifacts")
@RequiredArgsConstructor
public class AutomationArtifactController {

    private final AutomationArtifactService artifactService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:write', principal.roles)")
    public ApiResponse<AutomationStudioDto.ArtifactResponse> createArtifact(
            @Valid @RequestBody AutomationStudioDto.ArtifactRequest request) {
        return ApiResponse.ok(artifactService.createArtifact(request));
    }

    @GetMapping("/{artifactId}")
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:read', principal.roles)")
    public ApiResponse<AutomationStudioDto.ArtifactResponse> getArtifact(@PathVariable String artifactId) {
        return ApiResponse.ok(artifactService.getArtifact(artifactId));
    }
}

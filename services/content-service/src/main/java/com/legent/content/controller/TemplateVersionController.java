package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.content.domain.TemplateVersion;
import com.legent.content.dto.TemplateVersionDto;
import com.legent.content.service.TemplateVersionService;
import com.legent.content.service.TemplateWorkflowService;
import com.legent.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/templates/{templateId}/versions")
@RequiredArgsConstructor
public class TemplateVersionController {

    private final TemplateVersionService versionService;
    private final TemplateWorkflowService workflowService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('content:write', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public ApiResponse<TemplateVersionDto.Response> createVersion(
            @PathVariable String templateId,
            @Valid @RequestBody TemplateVersionDto.Create request) {
        TenantContext.requireWorkspaceId();
        TemplateVersion version = versionService.createVersion(templateId, request);
        return ApiResponse.ok(mapToResponse(version));
    }

    @PostMapping("/{versionNumber}/publish")
    @PreAuthorize("@rbacEvaluator.hasPermission('content:publish', principal.roles) or @rbacEvaluator.hasPermission('content:*', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public ApiResponse<TemplateVersionDto.Response> publishVersion(
            @PathVariable String templateId,
            @PathVariable Integer versionNumber) {
        TemplateVersion version = workflowService.publishTemplate(
                TenantContext.requireTenantId(),
                TenantContext.requireWorkspaceId(),
                templateId,
                versionNumber,
                false,
                null);
        return ApiResponse.ok(mapToResponse(version));
    }

    @GetMapping
    public ApiResponse<List<TemplateVersionDto.Response>> listVersions(@PathVariable String templateId) {
        TenantContext.requireWorkspaceId();
        List<TemplateVersion> versions = versionService.listVersions(templateId);
        return ApiResponse.ok(versions.stream().map(this::mapToResponse).toList());
    }

    @GetMapping("/latest")
    public ApiResponse<TemplateVersionDto.Response> getLatestVersion(@PathVariable String templateId) {
        TenantContext.requireWorkspaceId();
        TemplateVersion version = versionService.getLatestPublishedVersion(templateId);
        return ApiResponse.ok(mapToResponse(version));
    }

    @GetMapping("/compare")
    public ApiResponse<TemplateVersionDto.CompareResponse> compareVersions(
            @PathVariable String templateId,
            @RequestParam("left") Integer leftVersion,
            @RequestParam("right") Integer rightVersion) {
        TenantContext.requireWorkspaceId();
        return ApiResponse.ok(versionService.compareVersions(templateId, leftVersion, rightVersion));
    }

    private TemplateVersionDto.Response mapToResponse(TemplateVersion version) {
        TemplateVersionDto.Response response = new TemplateVersionDto.Response();
        response.setId(version.getId());
        response.setVersionNumber(version.getVersionNumber());
        response.setSubject(version.getSubject());
        response.setHtmlContent(version.getHtmlContent());
        response.setTextContent(version.getTextContent());
        response.setChanges(version.getChanges());
        response.setIsPublished(version.getIsPublished());
        response.setCreatedAt(version.getCreatedAt() != null ? version.getCreatedAt().toString() : null);
        response.setUpdatedAt(version.getUpdatedAt() != null ? version.getUpdatedAt().toString() : null);
        return response;
    }
}

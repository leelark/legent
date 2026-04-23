package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.content.domain.TemplateVersion;
import com.legent.content.dto.TemplateVersionDto;
import com.legent.content.service.TemplateVersionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/templates/{templateId}/versions")
@RequiredArgsConstructor
public class TemplateVersionController {

    private final TemplateVersionService versionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TemplateVersionDto.Response> createVersion(
            @PathVariable String templateId,
            @Valid @RequestBody TemplateVersionDto.Create request) {
        TemplateVersion version = versionService.createVersion(templateId, request);
        return ApiResponse.ok(mapToResponse(version));
    }

    @PostMapping("/{versionNumber}/publish")
    public ApiResponse<TemplateVersionDto.Response> publishVersion(
            @PathVariable String templateId,
            @PathVariable Integer versionNumber) {
        TemplateVersion version = versionService.publishVersion(templateId, versionNumber);
        return ApiResponse.ok(mapToResponse(version));
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

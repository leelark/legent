package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.content.domain.TemplateVersion;
import com.legent.content.dto.EmailStudioDto;
import com.legent.content.service.EmailRenderService;
import com.legent.content.service.TemplateVersionService;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Content rendering and versioning controller.
 * Provides endpoints for template rendering and version management.
 */
@Slf4j
@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/content")
@RequiredArgsConstructor
public class ContentController {

    private final EmailRenderService renderService;
    private final TemplateVersionService versionService;

    /**
     * Renders a template with personalization variables.
     * Used by campaign-service for email content generation.
     */
    @PostMapping("/{templateId}/render")
    public ApiResponse<RenderedContent> renderTemplate(
            @PathVariable String templateId,
            @RequestBody Map<String, Object> variables) {
        String tenantId = TenantContext.requireTenantId();
        EmailStudioDto.RenderRequest request = new EmailStudioDto.RenderRequest();
        request.setVariables(variables);
        request.setPublishedOnly(true);
        EmailStudioDto.RenderResponse rendered = renderService.render(tenantId, templateId, request);
        return ApiResponse.ok(new RenderedContent(rendered.getSubject(), rendered.getHtmlContent(), rendered.getTextContent()));
    }

    /**
     * Gets the latest published version of a template.
     * Used by campaign-service for fetching current template content.
     */
    @GetMapping("/{templateId}/versions/latest")
    public ApiResponse<TemplateVersionDto> getLatestVersion(@PathVariable String templateId) {
        TenantContext.requireTenantId();
        TemplateVersion latestVersion = versionService.getLatestPublishedVersion(templateId);
        return ApiResponse.ok(new TemplateVersionDto(
                latestVersion.getSubject(),
                latestVersion.getHtmlContent(),
                latestVersion.getTextContent()
        ));
    }

    public record RenderedContent(String subject, String htmlBody, String textBody) {}
    public record TemplateVersionDto(String subject, String htmlContent, String textContent) {}
}

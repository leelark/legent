package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.content.domain.EmailTemplate;
import com.legent.content.domain.TemplateVersion;
import com.legent.content.service.TemplateService;
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

    private final TemplateService templateService;
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

        EmailTemplate template = templateService.getTemplate(tenantId, templateId);
        TemplateService.RenderedParts rendered = templateService.renderTemplateParts(template, variables);
        return ApiResponse.ok(new RenderedContent(rendered.subject(), rendered.htmlContent(), rendered.textContent()));
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

package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.exception.NotFoundException;
import com.legent.content.domain.EmailTemplate;
import com.legent.content.domain.TemplateVersion;
import com.legent.content.repository.EmailTemplateRepository;
import com.legent.content.repository.TemplateVersionRepository;
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

    private final EmailTemplateRepository templateRepository;
    private final TemplateVersionRepository versionRepository;

    /**
     * Renders a template with personalization variables.
     * Used by campaign-service for email content generation.
     */
    @PostMapping("/{templateId}/render")
    public ApiResponse<RenderedContent> renderTemplate(
            @PathVariable String templateId,
            @RequestBody Map<String, Object> variables) {
        String tenantId = TenantContext.requireTenantId();
        
        EmailTemplate template = templateRepository.findByIdAndTenantIdAndDeletedAtIsNull(templateId, tenantId)
                .orElseThrow(() -> new NotFoundException("Template", templateId));
        
        // Simple variable substitution rendering
        String subject = template.getSubject();
        String htmlBody = template.getHtmlContent();
        String textBody = template.getTextContent();
        
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                if (subject != null) {
                    subject = subject.replace(placeholder, value);
                }
                if (htmlBody != null) {
                    htmlBody = htmlBody.replace(placeholder, value);
                }
                if (textBody != null) {
                    textBody = textBody.replace(placeholder, value);
                }
            }
        }
        
        return ApiResponse.ok(new RenderedContent(subject, htmlBody, textBody));
    }

    /**
     * Gets the latest published version of a template.
     * Used by campaign-service for fetching current template content.
     */
    @GetMapping("/{templateId}/versions/latest")
    public ApiResponse<TemplateVersionDto> getLatestVersion(@PathVariable String templateId) {
        String tenantId = TenantContext.requireTenantId();
        
        EmailTemplate template = templateRepository.findByIdAndTenantIdAndDeletedAtIsNull(templateId, tenantId)
                .orElseThrow(() -> new NotFoundException("Template", templateId));
        
        // Find the latest published version
        TemplateVersion latestVersion = versionRepository
                .findFirstByTemplate_IdAndTenantIdAndIsPublishedTrueOrderByVersionNumberDesc(templateId, tenantId)
                .orElse(null);
        
        if (latestVersion != null) {
            return ApiResponse.ok(new TemplateVersionDto(
                    latestVersion.getSubject(),
                    latestVersion.getHtmlContent(),
                    latestVersion.getTextContent()
            ));
        }
        
        // Fallback to template's current content if no published version exists
        return ApiResponse.ok(new TemplateVersionDto(
                template.getSubject(),
                template.getHtmlContent(),
                template.getTextContent()
        ));
    }

    public record RenderedContent(String subject, String htmlBody, String textBody) {}
    public record TemplateVersionDto(String subject, String htmlContent, String textContent) {}
}

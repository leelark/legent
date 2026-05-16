package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.content.domain.TemplateVersion;
import com.legent.content.dto.EmailStudioDto;
import com.legent.content.service.EmailRenderService;
import com.legent.content.service.TemplateVersionService;
import com.legent.security.TenantContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Locale;

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

    @Value("${legent.internal.api-token}")
    private String internalApiToken;

    @PostConstruct
    void validateInternalApiToken() {
        if (internalApiToken == null || internalApiToken.isBlank() || isPlaceholderToken(internalApiToken)) {
            throw new IllegalStateException("legent.internal.api-token must be configured with a non-placeholder secret");
        }
    }

    /**
     * Renders a template with personalization variables.
     * Used by campaign-service for email content generation.
     */
    @PostMapping("/{templateId}/render")
    @PreAuthorize("@rbacEvaluator.hasPermission('content:read', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public ApiResponse<RenderedContent> renderTemplate(
            @PathVariable String templateId,
            @RequestBody Map<String, Object> variables) {
        return renderTemplateForTenant(TenantContext.requireTenantId(), TenantContext.requireWorkspaceId(), templateId, variables);
    }

    @PostMapping("/{templateId}/render/internal")
    @PreAuthorize("permitAll()")
    public ApiResponse<RenderedContent> renderTemplateInternal(
            @PathVariable String templateId,
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, Object> variables) {
        if (token == null || !token.equals(internalApiToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal token");
        }
        return renderTemplateForTenant(TenantContext.requireTenantId(), TenantContext.requireWorkspaceId(), templateId, variables);
    }

    private ApiResponse<RenderedContent> renderTemplateForTenant(String tenantId, String workspaceId, String templateId, Map<String, Object> variables) {
        EmailStudioDto.RenderRequest request = new EmailStudioDto.RenderRequest();
        request.setVariables(variables);
        request.setPublishedOnly(true);
        EmailStudioDto.RenderResponse rendered = renderService.render(tenantId, workspaceId, templateId, request);
        return ApiResponse.ok(new RenderedContent(rendered.getSubject(), rendered.getHtmlContent(), rendered.getTextContent()));
    }

    /**
     * Gets the latest published version of a template.
     * Used by campaign-service for fetching current template content.
     */
    @GetMapping("/{templateId}/versions/latest")
    @PreAuthorize("@rbacEvaluator.hasPermission('content:read', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public ApiResponse<TemplateVersionDto> getLatestVersion(@PathVariable String templateId) {
        TenantContext.requireTenantId();
        TenantContext.requireWorkspaceId();
        TemplateVersion latestVersion = versionService.getLatestPublishedVersion(templateId);
        return ApiResponse.ok(new TemplateVersionDto(
                latestVersion.getSubject(),
                latestVersion.getHtmlContent(),
                latestVersion.getTextContent()
        ));
    }

    public record RenderedContent(String subject, String htmlBody, String textBody) {}
    public record TemplateVersionDto(String subject, String htmlContent, String textContent) {}

    private boolean isPlaceholderToken(String token) {
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("dev-token")
                || normalized.contains("change_me")
                || normalized.contains("changeme")
                || normalized.contains("replace_in_production")
                || normalized.equals("password");
    }
}

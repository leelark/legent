package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.security.InternalApiTokenValidator;
import com.legent.common.security.InternalServiceIdentity;
import com.legent.content.domain.TemplateVersion;
import com.legent.content.dto.EmailStudioDto;
import com.legent.content.service.EmailRenderService;
import com.legent.content.service.RenderedContentSnapshotService;
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

/**
 * Content rendering and versioning controller.
 * Provides endpoints for template rendering and version management.
 */
@Slf4j
@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/content")
@RequiredArgsConstructor
public class ContentController {

    private static final java.util.Set<String> ALLOWED_TEMPLATE_RENDER_SERVICES = java.util.Set.of("campaign-service");
    private static final java.util.Set<String> ALLOWED_SNAPSHOT_CREATE_SERVICES = java.util.Set.of("campaign-service");
    private static final java.util.Set<String> ALLOWED_SNAPSHOT_READ_SERVICES = java.util.Set.of("campaign-service", "delivery-service");

    private final EmailRenderService renderService;
    private final TemplateVersionService versionService;
    private final RenderedContentSnapshotService snapshotService;

    @Value("${legent.internal.api-token}")
    private String internalApiToken;

    @PostConstruct
    void validateInternalApiToken() {
        InternalApiTokenValidator.requireConfigured("legent.internal.api-token", internalApiToken);
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
            @RequestHeader(name = InternalServiceIdentity.HEADER_SERVICE, required = false) String internalService,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SIGNATURE_TIMESTAMP, required = false) String signatureTimestamp,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SIGNATURE, required = false) String signature,
            @RequestBody Map<String, Object> variables) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        requireInternalIdentity(
                token,
                internalService,
                signatureTimestamp,
                signature,
                ALLOWED_TEMPLATE_RENDER_SERVICES,
                tenantId,
                workspaceId,
                InternalServiceIdentity.scopedAction(
                        InternalServiceIdentity.ACTION_CONTENT_TEMPLATE_RENDER,
                        templateId));
        return renderTemplateForTenant(tenantId, workspaceId, templateId, variables);
    }

    @PostMapping("/rendered-content/internal")
    @PreAuthorize("permitAll()")
    public ApiResponse<com.legent.common.event.EmailContentReference> createRenderedContentSnapshot(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SERVICE, required = false) String internalService,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SIGNATURE_TIMESTAMP, required = false) String signatureTimestamp,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SIGNATURE, required = false) String signature,
            @RequestBody RenderedContentSnapshotCreateRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        requireInternalIdentity(
                token,
                internalService,
                signatureTimestamp,
                signature,
                ALLOWED_SNAPSHOT_CREATE_SERVICES,
                tenantId,
                workspaceId,
                InternalServiceIdentity.scopedAction(
                        InternalServiceIdentity.ACTION_CONTENT_RENDERED_SNAPSHOT_CREATE,
                        request == null ? "" : request.messageId()));
        RenderedContentSnapshotService.SnapshotRequest snapshotRequest =
                new RenderedContentSnapshotService.SnapshotRequest(
                        request.tenantId(),
                        request.workspaceId(),
                        request.campaignId(),
                        request.jobId(),
                        request.batchId(),
                        request.messageId(),
                        request.contentId(),
                        request.subject(),
                        request.htmlBody(),
                        request.textBody());
        return ApiResponse.ok(snapshotService.create(tenantId, workspaceId, snapshotRequest,
                Boolean.TRUE.equals(request.inlineFallbackIncluded())));
    }

    @GetMapping("/rendered-content/{referenceId}/internal")
    @PreAuthorize("permitAll()")
    public ApiResponse<RenderedContentSnapshotService.StoredRenderedContent> getRenderedContentSnapshot(
            @PathVariable String referenceId,
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SERVICE, required = false) String internalService,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SIGNATURE_TIMESTAMP, required = false) String signatureTimestamp,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SIGNATURE, required = false) String signature) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        requireInternalIdentity(
                token,
                internalService,
                signatureTimestamp,
                signature,
                ALLOWED_SNAPSHOT_READ_SERVICES,
                tenantId,
                workspaceId,
                InternalServiceIdentity.scopedAction(
                        InternalServiceIdentity.ACTION_CONTENT_RENDERED_SNAPSHOT_READ,
                        referenceId));
        return ApiResponse.ok(snapshotService.read(
                tenantId,
                workspaceId,
                referenceId));
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
    public record RenderedContentSnapshotCreateRequest(String tenantId,
                                                       String workspaceId,
                                                       String campaignId,
                                                       String jobId,
                                                       String batchId,
                                                       String messageId,
                                                       String contentId,
                                                       String subject,
                                                       String htmlBody,
                                                       String textBody,
                                                       Boolean inlineFallbackIncluded) {}

    private void requireInternalIdentity(String token,
                                         String internalService,
                                         String signatureTimestamp,
                                         String signature,
                                         java.util.Set<String> allowedServices,
                                         String tenantId,
                                         String workspaceId,
                                         String action) {
        if (!InternalServiceIdentity.matches(
                internalApiToken,
                token,
                internalService,
                allowedServices,
                tenantId,
                workspaceId,
                action,
                signatureTimestamp,
                signature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal service identity");
        }
    }
}

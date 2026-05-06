package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import com.legent.content.domain.EmailTemplate;
import com.legent.content.dto.TemplateDto;
import com.legent.content.dto.TemplateWorkflowDto;
import com.legent.content.service.TemplateService;
import com.legent.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TemplateDto.Response> createTemplate(@Valid @RequestBody TemplateDto.Create request) {
        EmailTemplate template = templateService.createTemplate(request);
        return ApiResponse.ok(mapToResponse(template));
    }

    @GetMapping("/{id}")
    public ApiResponse<TemplateDto.Response> getTemplate(@PathVariable String id) {
        String tenantId = TenantContext.requireTenantId();
        EmailTemplate template = templateService.getTemplate(tenantId, id);
        return ApiResponse.ok(mapToResponse(template));
    }

    @PutMapping("/{id}")
    public ApiResponse<TemplateDto.Response> updateTemplate(@PathVariable String id, @Valid @RequestBody TemplateDto.Update request) {
        String tenantId = TenantContext.requireTenantId();
        EmailTemplate template = templateService.updateTemplate(tenantId, id, request);
        return ApiResponse.ok(mapToResponse(template));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@PathVariable String id) {
        String tenantId = TenantContext.requireTenantId();
        templateService.deleteTemplate(tenantId, id);
    }

    @PostMapping("/{id}/clone")
    public ApiResponse<TemplateDto.Response> cloneTemplate(@PathVariable String id) {
        String tenantId = TenantContext.requireTenantId();
        EmailTemplate template = templateService.cloneTemplate(tenantId, id);
        return ApiResponse.ok(mapToResponse(template));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<TemplateDto.Response> archiveTemplate(@PathVariable String id,
                                                             @RequestBody(required = false) Map<String, Object> request) {
        String tenantId = TenantContext.requireTenantId();
        EmailTemplate template = templateService.archiveTemplate(tenantId, id);
        return ApiResponse.ok(mapToResponse(template));
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<TemplateDto.Response> restoreTemplate(@PathVariable String id,
                                                             @RequestBody(required = false) Map<String, Object> request) {
        String tenantId = TenantContext.requireTenantId();
        EmailTemplate template = templateService.restoreTemplate(tenantId, id);
        return ApiResponse.ok(mapToResponse(template));
    }

    @GetMapping
    public PagedResponse<TemplateDto.Response> listTemplates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = TenantContext.requireTenantId();
        Page<EmailTemplate> templates = templateService.listTemplates(tenantId, PageRequest.of(page, Math.min(size, AppConstants.MAX_PAGE_SIZE)));
        return PagedResponse.of(
                templates.getContent().stream().map(this::mapToResponse).toList(),
                page,
                size,
                templates.getTotalElements(),
                templates.getTotalPages()
        );
    }

    @GetMapping("/search")
    public ApiResponse<List<TemplateDto.Response>> searchTemplates(@RequestParam String q) {
        String tenantId = TenantContext.requireTenantId();
        List<EmailTemplate> templates = templateService.searchTemplates(tenantId, q);
        return ApiResponse.ok(templates.stream().map(this::mapToResponse).toList());
    }

    @PostMapping("/import/html")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TemplateDto.Response> importTemplate(@Valid @RequestBody TemplateWorkflowDto.ImportHtmlRequest request) {
        EmailTemplate template = templateService.importTemplate(request);
        return ApiResponse.ok(mapToResponse(template));
    }

    @GetMapping("/{id}/export/html")
    public ApiResponse<TemplateWorkflowDto.ExportHtmlResponse> exportTemplate(@PathVariable String id) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(templateService.exportTemplate(tenantId, id));
    }

    @PostMapping("/{id}/preview")
    public ApiResponse<TemplateWorkflowDto.PreviewResponse> previewTemplate(
            @PathVariable String id,
            @RequestBody(required = false) TemplateWorkflowDto.PreviewRequest request) {
        String tenantId = TenantContext.requireTenantId();
        TemplateWorkflowDto.PreviewRequest safeRequest = request != null ? request : new TemplateWorkflowDto.PreviewRequest();
        return ApiResponse.ok(templateService.previewTemplate(
                tenantId,
                id,
                safeRequest.getVariables(),
                safeRequest.getMode(),
                safeRequest.getDarkMode()
        ));
    }

    @PostMapping("/validate")
    public ApiResponse<TemplateWorkflowDto.ValidateResponse> validateTemplate(
            @RequestBody(required = false) TemplateWorkflowDto.ValidateRequest request) {
        String htmlContent = request != null ? request.getHtmlContent() : "";
        return ApiResponse.ok(templateService.validateTemplateHtml(htmlContent));
    }

    @PostMapping("/{id}/test-send")
    public ApiResponse<Map<String, String>> testSend(
            @PathVariable String id,
            @Valid @RequestBody TemplateWorkflowDto.TestSendRequest request) {
        String tenantId = TenantContext.requireTenantId();
        templateService.sendTestEmail(tenantId, id, request.getEmail(), request.getSubjectOverride(), request.getVariables());
        return ApiResponse.ok(Map.of(
                "status", "queued",
                "message", "Test email queued for delivery"
        ));
    }

    private TemplateDto.Response mapToResponse(EmailTemplate template) {
        TemplateDto.Response response = new TemplateDto.Response();
        response.setId(template.getId());
        response.setName(template.getName());
        response.setSubject(template.getSubject());
        response.setHtmlContent(template.getHtmlContent());
        response.setTextContent(template.getTextContent());
        if (template.getStatus() != null) {
            response.setStatus(template.getStatus().name());
        }
        if (template.getTemplateType() != null) {
            response.setTemplateType(template.getTemplateType().name());
        }
        response.setCategory(template.getCategory());
        response.setTags(template.getTags());
        response.setMetadata(template.getMetadata());
        response.setDraftSubject(template.getDraftSubject());
        response.setDraftHtmlContent(template.getDraftHtmlContent());
        response.setDraftTextContent(template.getDraftTextContent());
        response.setApprovalRequired(template.isApprovalRequired());
        response.setCurrentApprover(template.getCurrentApprover());
        response.setLastPublishedVersion(template.getLastPublishedVersion());
        if (template.getLastPublishedAt() != null) {
            response.setLastPublishedAt(template.getLastPublishedAt().toString());
        }
        if (template.getCreatedAt() != null) {
            response.setCreatedAt(template.getCreatedAt().toString());
        }
        if (template.getUpdatedAt() != null) {
            response.setUpdatedAt(template.getUpdatedAt().toString());
        }
        return response;
    }
}

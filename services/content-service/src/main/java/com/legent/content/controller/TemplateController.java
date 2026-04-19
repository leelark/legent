package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import com.legent.content.domain.EmailTemplate;
import com.legent.content.dto.TemplateDto;
import com.legent.content.service.TemplateService;
import com.legent.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;
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

    private TemplateDto.Response mapToResponse(EmailTemplate template) {
        TemplateDto.Response response = new TemplateDto.Response();
        response.setId(template.getId());
        response.setName(template.getName());
        response.setSubject(template.getSubject());
        response.setStatus(template.getStatus().name());
        response.setTemplateType(template.getTemplateType().name());
        response.setCategory(template.getCategory());
        response.setTags(template.getTags());
        response.setMetadata(template.getMetadata());
        response.setCreatedAt(template.getCreatedAt().toString());
        response.setUpdatedAt(template.getUpdatedAt().toString());
        return response;
    }
}
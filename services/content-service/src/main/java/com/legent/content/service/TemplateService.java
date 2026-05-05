package com.legent.content.service;

import com.legent.content.domain.EmailTemplate;
import com.legent.content.dto.TemplateDto;
import com.legent.content.dto.TemplateVersionDto;
import com.legent.content.repository.EmailTemplateRepository;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ConflictException;
import com.legent.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.thymeleaf.TemplateEngine;
import java.util.Objects;
import org.thymeleaf.context.Context;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EmailTemplateRepository templateRepository;
    private final TemplateEngine stringTemplateEngine;
    private final TemplateVersionService versionService;

    @Transactional
    public EmailTemplate createTemplate(TemplateDto.Create request) {
        String tenantId = TenantContext.requireTenantId();

        if (templateRepository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, request.getName())) {
            throw new ConflictException("Template with name '" + request.getName() + "' already exists");
        }

        EmailTemplate template = new EmailTemplate();
        template.setTenantId(tenantId);
        template.setName(request.getName());
        template.setSubject(request.getSubject());
        String html = request.getBody() != null ? request.getBody() : request.getHtmlContent();
        template.setHtmlContent(html);
        template.setTextContent(request.getTextContent());
        template.setCategory(request.getCategory());
        template.setTags(request.getTags());
        template.setMetadata(normalizeJsonMetadata(request.getMetadata()));
        if (request.getCreatedBy() != null && !request.getCreatedBy().isBlank()) {
            template.setCreatedBy(request.getCreatedBy());
        } else if (TenantContext.getUserId() != null) {
            template.setCreatedBy(TenantContext.getUserId());
        }

        EmailTemplate savedTemplate = templateRepository.save(template);
        return Objects.requireNonNull(savedTemplate, "Saved template cannot be null");
    }

    @Cacheable(value = "templates", key = "#tenantId + ':' + #id")
    public EmailTemplate getTemplate(String tenantId, String id) {
        return templateRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Template not found"));
    }

    @Transactional
    @CacheEvict(value = "templates", key = "#tenantId + ':' + #id")
    public EmailTemplate updateTemplate(String tenantId, String id, TemplateDto.Update request) {
        EmailTemplate template = getTemplate(tenantId, id);

        if (request.getName() != null && !request.getName().equals(template.getName())) {
            if (templateRepository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, request.getName())) {
                throw new ConflictException("Template with name '" + request.getName() + "' already exists");
            }
            template.setName(request.getName());
        }

        if (request.getSubject() != null) {
            template.setSubject(request.getSubject());
        }
        if (request.getHtmlContent() != null) {
            template.setHtmlContent(request.getHtmlContent());
        }
        if (request.getTextContent() != null) {
            template.setTextContent(request.getTextContent());
        }
        if (request.getCategory() != null) {
            template.setCategory(request.getCategory());
        }
        if (request.getTags() != null) {
            template.setTags(request.getTags());
        }
        if (request.getMetadata() != null) {
            template.setMetadata(normalizeJsonMetadata(request.getMetadata()));
        }

        boolean contentChanged = request.getHtmlContent() != null || request.getSubject() != null;
        
        
        EmailTemplate savedTemplate = templateRepository.save(template);

        if (contentChanged) {
            TemplateVersionDto.Create versionRequest = new TemplateVersionDto.Create();
            versionRequest.setSubject(savedTemplate.getSubject());
            versionRequest.setHtmlContent(savedTemplate.getHtmlContent());
            versionRequest.setTextContent(savedTemplate.getTextContent());
            versionRequest.setChanges("Automatic update");
            versionRequest.setPublish(true);
            versionService.createVersion(savedTemplate.getId(), versionRequest);
        }

        return savedTemplate;
    }

    @Transactional
    @CacheEvict(value = "templates", key = "#tenantId + ':' + #id")
    public void deleteTemplate(String tenantId, String id) {
        EmailTemplate template = getTemplate(tenantId, id);
        template.setDeletedAt(java.time.Instant.now());
        templateRepository.save(template);
    }

    public Page<EmailTemplate> listTemplates(String tenantId, Pageable pageable) {
        return templateRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable);
    }

    public List<EmailTemplate> searchTemplates(String tenantId, String query) {
        return templateRepository.searchByName(tenantId, query);
    }

    private String normalizeJsonMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return "{}";
        }
        try {
            OBJECT_MAPPER.readTree(metadata);
            return metadata;
        } catch (Exception ex) {
            throw new IllegalArgumentException("metadata must be a valid JSON object or array");
        }
    }

    /**
     * Render template HTML with personalization tokens.
     * @param template the EmailTemplate
     * @param variables personalization variables
     * @return rendered HTML
     */
    public String renderTemplate(EmailTemplate template, java.util.Map<String, Object> variables) {
        Context context = new Context();
        if (variables != null) {
            context.setVariables(variables);
        }
        return stringTemplateEngine.process(template.getHtmlContent(), context);
    }
}

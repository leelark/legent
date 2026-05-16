package com.legent.content.service;

import com.legent.common.constant.AppConstants;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.content.domain.EmailTemplate;
import com.legent.content.dto.TemplateDto;
import com.legent.content.dto.TemplateWorkflowDto;
import com.legent.content.dto.TemplateVersionDto;
import com.legent.content.repository.EmailTemplateRepository;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern HREF_PATTERN = Pattern.compile("href\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_PATTERN = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALT_ATTR_PATTERN = Pattern.compile("\\balt\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern MERGE_TAG_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    private final EmailTemplateRepository templateRepository;
    private final TemplateEngine stringTemplateEngine;
    private final TemplateVersionService versionService;
    private final EventPublisher eventPublisher;

    @Transactional
    public EmailTemplate createTemplate(TemplateDto.Create request) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();

        if (templateRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(tenantId, workspaceId, request.getName())) {
            throw new ConflictException("Template with name '" + request.getName() + "' already exists");
        }

        EmailTemplate template = new EmailTemplate();
        template.setTenantId(tenantId);
        template.setWorkspaceId(workspaceId);
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

    public EmailTemplate getTemplate(String tenantId, String id) {
        return getTemplate(tenantId, TenantContext.requireWorkspaceId(), id);
    }

    public EmailTemplate getTemplate(String tenantId, String workspaceId, String id) {
        return templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(id, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template not found"));
    }

    @Transactional
    public EmailTemplate updateTemplate(String tenantId, String id, TemplateDto.Update request) {
        return updateTemplate(tenantId, TenantContext.requireWorkspaceId(), id, request);
    }

    @Transactional
    public EmailTemplate updateTemplate(String tenantId, String workspaceId, String id, TemplateDto.Update request) {
        EmailTemplate template = getTemplate(tenantId, workspaceId, id);

        if (request.getName() != null && !request.getName().equals(template.getName())) {
            if (templateRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(tenantId, workspaceId, request.getName())) {
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

        boolean contentChanged = request.getHtmlContent() != null || request.getSubject() != null || request.getTextContent() != null;
        
        
        EmailTemplate savedTemplate = templateRepository.save(template);

        if (contentChanged) {
            TemplateVersionDto.Create versionRequest = new TemplateVersionDto.Create();
            versionRequest.setSubject(savedTemplate.getSubject());
            versionRequest.setHtmlContent(savedTemplate.getHtmlContent());
            versionRequest.setTextContent(savedTemplate.getTextContent());
            versionRequest.setChanges("Autosave snapshot");
            versionRequest.setPublish(false);
            versionService.createVersion(savedTemplate.getId(), versionRequest);
            savedTemplate.setStatus(EmailTemplate.TemplateStatus.DRAFT);
            savedTemplate.setDraftSubject(savedTemplate.getSubject());
            savedTemplate.setDraftHtmlContent(savedTemplate.getHtmlContent());
            savedTemplate.setDraftTextContent(savedTemplate.getTextContent());
            savedTemplate = templateRepository.save(savedTemplate);
        }

        return savedTemplate;
    }

    @Transactional
    public void deleteTemplate(String tenantId, String id) {
        deleteTemplate(tenantId, TenantContext.requireWorkspaceId(), id);
    }

    @Transactional
    public void deleteTemplate(String tenantId, String workspaceId, String id) {
        EmailTemplate template = getTemplate(tenantId, workspaceId, id);
        template.setDeletedAt(java.time.Instant.now());
        templateRepository.save(template);
    }

    @Transactional
    public EmailTemplate cloneTemplate(String tenantId, String id) {
        return cloneTemplate(tenantId, TenantContext.requireWorkspaceId(), id);
    }

    @Transactional
    public EmailTemplate cloneTemplate(String tenantId, String workspaceId, String id) {
        EmailTemplate source = getTemplate(tenantId, workspaceId, id);

        EmailTemplate clone = new EmailTemplate();
        clone.setTenantId(tenantId);
        clone.setWorkspaceId(workspaceId);
        clone.setName(resolveCloneName(tenantId, workspaceId, source.getName()));
        clone.setSubject(source.getSubject());
        clone.setHtmlContent(source.getHtmlContent());
        clone.setTextContent(source.getTextContent());
        clone.setTemplateType(source.getTemplateType());
        clone.setCategory(source.getCategory());
        clone.setTags(source.getTags() == null ? null : new ArrayList<>(source.getTags()));
        clone.setMetadata(source.getMetadata());
        clone.setDraftSubject(source.getDraftSubject());
        clone.setDraftHtmlContent(source.getDraftHtmlContent());
        clone.setDraftTextContent(source.getDraftTextContent());
        clone.setApprovalRequired(source.isApprovalRequired());
        clone.setStatus(EmailTemplate.TemplateStatus.DRAFT);
        clone.setCurrentApprover(null);
        clone.setLastPublishedVersion(null);
        clone.setLastPublishedAt(null);
        clone.setCreatedBy(TenantContext.getUserId());

        return templateRepository.save(clone);
    }

    @Transactional
    public EmailTemplate archiveTemplate(String tenantId, String id) {
        return archiveTemplate(tenantId, TenantContext.requireWorkspaceId(), id);
    }

    @Transactional
    public EmailTemplate archiveTemplate(String tenantId, String workspaceId, String id) {
        EmailTemplate template = getTemplate(tenantId, workspaceId, id);
        template.setStatus(EmailTemplate.TemplateStatus.ARCHIVED);
        return templateRepository.save(template);
    }

    @Transactional
    public EmailTemplate restoreTemplate(String tenantId, String id) {
        return restoreTemplate(tenantId, TenantContext.requireWorkspaceId(), id);
    }

    @Transactional
    public EmailTemplate restoreTemplate(String tenantId, String workspaceId, String id) {
        EmailTemplate template = getTemplate(tenantId, workspaceId, id);
        if (template.getStatus() == EmailTemplate.TemplateStatus.ARCHIVED) {
            template.setStatus(EmailTemplate.TemplateStatus.DRAFT);
        }
        return templateRepository.save(template);
    }

    public Page<EmailTemplate> listTemplates(String tenantId, Pageable pageable) {
        return listTemplates(tenantId, TenantContext.requireWorkspaceId(), pageable);
    }

    public Page<EmailTemplate> listTemplates(String tenantId, String workspaceId, Pageable pageable) {
        return templateRepository.findByTenantIdAndWorkspaceIdAndDeletedAtIsNull(tenantId, workspaceId, pageable);
    }

    public List<EmailTemplate> searchTemplates(String tenantId, String query) {
        return searchTemplates(tenantId, TenantContext.requireWorkspaceId(), query);
    }

    public List<EmailTemplate> searchTemplates(String tenantId, String workspaceId, String query) {
        return templateRepository.searchByName(tenantId, workspaceId, query);
    }

    @Transactional
    public EmailTemplate importTemplate(TemplateWorkflowDto.ImportHtmlRequest request) {
        TemplateDto.Create create = new TemplateDto.Create();
        create.setName(request.getName());
        create.setSubject(request.getSubject());
        create.setBody(request.getHtmlContent());
        create.setHtmlContent(request.getHtmlContent());
        create.setTextContent(request.getTextContent());
        create.setCategory(request.getCategory());
        create.setTags(request.getTags());
        create.setMetadata(request.getMetadata());
        EmailTemplate template = createTemplate(create);

        TemplateVersionDto.Create version = new TemplateVersionDto.Create();
        version.setSubject(template.getSubject());
        version.setHtmlContent(template.getHtmlContent());
        version.setTextContent(template.getTextContent());
        version.setChanges("Imported from HTML");
        version.setPublish(false);
        versionService.createVersion(template.getId(), version);

        return templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(template.getId(), template.getTenantId(), template.getWorkspaceId())
                .orElse(template);
    }

    public TemplateWorkflowDto.ExportHtmlResponse exportTemplate(String tenantId, String templateId) {
        return exportTemplate(tenantId, TenantContext.requireWorkspaceId(), templateId);
    }

    public TemplateWorkflowDto.ExportHtmlResponse exportTemplate(String tenantId, String workspaceId, String templateId) {
        EmailTemplate template = getTemplate(tenantId, workspaceId, templateId);
        TemplateWorkflowDto.ExportHtmlResponse response = new TemplateWorkflowDto.ExportHtmlResponse();
        response.setId(template.getId());
        response.setName(template.getName());
        response.setSubject(template.getSubject());
        response.setHtmlContent(template.getHtmlContent());
        response.setTextContent(template.getTextContent());
        response.setExportedAt(Instant.now().toString());
        return response;
    }

    public TemplateWorkflowDto.PreviewResponse previewTemplate(String tenantId, String templateId, Map<String, Object> variables, String mode, Boolean darkMode) {
        EmailTemplate template = getTemplate(tenantId, TenantContext.requireWorkspaceId(), templateId);
        RenderedParts rendered = renderTemplateParts(template, variables);

        List<String> warnings = new ArrayList<>();
        if (mode != null && !mode.isBlank() && !"desktop".equalsIgnoreCase(mode) && !"tablet".equalsIgnoreCase(mode) && !"mobile".equalsIgnoreCase(mode)) {
            warnings.add("Unknown preview mode: " + mode + ". Fallback to desktop.");
        }
        if (Boolean.TRUE.equals(darkMode)) {
            warnings.add("Dark-mode preview is heuristic only; verify in inbox clients.");
        }

        TemplateWorkflowDto.PreviewResponse response = new TemplateWorkflowDto.PreviewResponse();
        response.setSubject(rendered.subject());
        response.setHtmlContent(rendered.htmlContent());
        response.setTextContent(rendered.textContent());
        response.setWarnings(warnings);
        return response;
    }

    public TemplateWorkflowDto.ValidateResponse validateTemplateHtml(String htmlContent) {
        String html = htmlContent == null ? "" : htmlContent;
        List<String> warnings = new ArrayList<>();
        List<String> brokenLinks = new ArrayList<>();

        Matcher hrefMatcher = HREF_PATTERN.matcher(html);
        int linkCount = 0;
        while (hrefMatcher.find()) {
            linkCount++;
            String link = hrefMatcher.group(1);
            if (link == null || link.isBlank()) {
                brokenLinks.add("Empty href");
                continue;
            }
            String normalized = link.trim();
            if (!(normalized.startsWith("http://") || normalized.startsWith("https://") || normalized.startsWith("mailto:") || normalized.startsWith("{{"))) {
                brokenLinks.add(normalized);
            }
        }

        Matcher imgMatcher = IMG_PATTERN.matcher(html);
        int imageCount = 0;
        int imagesMissingAlt = 0;
        while (imgMatcher.find()) {
            imageCount++;
            String imgTag = imgMatcher.group();
            if (imgTag != null && !ALT_ATTR_PATTERN.matcher(imgTag).find()) {
                imagesMissingAlt++;
            }
        }

        Matcher mergeTagMatcher = MERGE_TAG_PATTERN.matcher(html);
        while (mergeTagMatcher.find()) {
            String tagName = mergeTagMatcher.group(1);
            if (tagName == null || tagName.isBlank()) {
                warnings.add("Empty merge tag detected");
            }
        }

        if (html.toLowerCase().contains("<script")) {
            warnings.add("Script tags are not supported in email clients.");
        }

        TemplateWorkflowDto.ValidateResponse response = new TemplateWorkflowDto.ValidateResponse();
        response.setLinkCount(linkCount);
        response.setBrokenLinkCount(brokenLinks.size());
        response.setImageCount(imageCount);
        response.setImagesMissingAlt(imagesMissingAlt);
        response.setBrokenLinks(brokenLinks);
        response.setWarnings(warnings);
        return response;
    }

    public void sendTestEmail(String tenantId, String templateId, String toEmail, String subjectOverride, Map<String, Object> variables) {
        EmailTemplate template = getTemplate(tenantId, TenantContext.requireWorkspaceId(), templateId);
        RenderedParts rendered = renderTemplateParts(template, variables);

        Map<String, Object> payload = Map.of(
                "email", toEmail,
                "subscriberId", "template-test",
                "campaignId", "template-preview",
                "messageId", "tpl-test-" + templateId + "-" + Instant.now().toEpochMilli(),
                "subject", subjectOverride != null && !subjectOverride.isBlank() ? subjectOverride : rendered.subject(),
                "htmlBody", rendered.htmlContent()
        );

        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
                tenantId,
                "content-service",
                payload
        );
        eventPublisher.publish(AppConstants.TOPIC_EMAIL_SEND_REQUESTED, envelope);
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

    private String resolveCloneName(String tenantId, String workspaceId, String baseName) {
        String seed = (baseName == null || baseName.isBlank()) ? "Template" : baseName.trim();
        String candidate = seed + " Copy";
        int index = 2;
        while (templateRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(tenantId, workspaceId, candidate)) {
            candidate = seed + " Copy " + index;
            index++;
        }
        return candidate;
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
        String html = stringTemplateEngine.process(template.getHtmlContent(), context);
        return replaceMergeTags(html, variables);
    }

    public RenderedParts renderTemplateParts(EmailTemplate template, Map<String, Object> variables) {
        Context context = new Context();
        if (variables != null) {
            context.setVariables(variables);
        }

        String renderedSubject = template.getSubject() != null ? template.getSubject() : "";
        String renderedHtml = template.getHtmlContent() != null ? stringTemplateEngine.process(template.getHtmlContent(), context) : "";
        String renderedText = template.getTextContent() != null ? template.getTextContent() : "";

        renderedSubject = replaceMergeTags(renderedSubject, variables);
        renderedHtml = replaceMergeTags(renderedHtml, variables);
        renderedText = replaceMergeTags(renderedText, variables);

        return new RenderedParts(renderedSubject, renderedHtml, renderedText);
    }

    private String replaceMergeTags(String content, Map<String, Object> variables) {
        if (content == null || variables == null || variables.isEmpty()) {
            return content;
        }
        String rendered = content;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            rendered = rendered.replace(placeholder, value);
        }
        return rendered;
    }

    public record RenderedParts(String subject, String htmlContent, String textContent) {}
}

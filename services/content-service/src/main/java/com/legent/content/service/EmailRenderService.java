package com.legent.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.content.domain.BrandKit;
import com.legent.content.domain.ContentBlock;
import com.legent.content.domain.ContentBlockVersion;
import com.legent.content.domain.ContentSnippet;
import com.legent.content.domain.EmailTemplate;
import com.legent.content.domain.RenderValidationReport;
import com.legent.content.domain.TemplateVersion;
import com.legent.content.dto.EmailStudioDto;
import com.legent.content.repository.BrandKitRepository;
import com.legent.content.repository.ContentBlockRepository;
import com.legent.content.repository.ContentBlockVersionRepository;
import com.legent.content.repository.ContentSnippetRepository;
import com.legent.content.repository.EmailTemplateRepository;
import com.legent.content.repository.RenderValidationReportRepository;
import com.legent.content.repository.TemplateVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EmailRenderService {

    private static final Pattern SNIPPET_PATTERN = Pattern.compile("\\{\\{\\s*snippet\\.([a-zA-Z0-9_.-]+)\\s*}}");
    private static final Pattern BLOCK_PATTERN = Pattern.compile("\\{\\{\\s*block\\.([a-zA-Z0-9_-]+)\\s*}}");
    private static final Pattern DYNAMIC_PATTERN = Pattern.compile("\\{\\{\\s*dynamic\\.([a-zA-Z0-9_.-]+)\\s*}}");

    private final EmailTemplateRepository templateRepository;
    private final TemplateVersionRepository versionRepository;
    private final ContentSnippetRepository snippetRepository;
    private final ContentBlockRepository blockRepository;
    private final ContentBlockVersionRepository blockVersionRepository;
    private final BrandKitRepository brandKitRepository;
    private final RenderValidationReportRepository reportRepository;
    private final PersonalizationTokenService tokenService;
    private final DynamicContentRuleService dynamicRuleService;
    private final EmailContentValidationService validationService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public EmailStudioDto.RenderResponse render(String tenantId, String templateId, EmailStudioDto.RenderRequest request) {
        RenderComputation computation = compute(tenantId, templateId, request != null ? request : new EmailStudioDto.RenderRequest());
        return computation.response();
    }

    @Transactional
    public EmailStudioDto.ValidationResponse validateAndPersist(String tenantId, String templateId, EmailStudioDto.RenderRequest request) {
        RenderComputation computation = compute(tenantId, templateId, request != null ? request : new EmailStudioDto.RenderRequest());
        EmailStudioDto.ValidationResponse validation = validationService.validate(
                computation.response().getHtmlContent(),
                computation.response().getTextContent());
        List<String> errors = new ArrayList<>(validation.getErrors());
        errors.addAll(computation.response().getErrors());
        List<String> warnings = new ArrayList<>(validation.getWarnings());
        warnings.addAll(computation.response().getWarnings());
        validation.setErrors(errors);
        validation.setWarnings(warnings);
        validation.setStatus(errors.isEmpty() ? "PASS" : "FAIL");
        persistReport(tenantId, templateId, validation);
        return validation;
    }

    @Transactional(readOnly = true)
    public RenderComputation compute(String tenantId, String templateId, EmailStudioDto.RenderRequest request) {
        EmailTemplate template = templateRepository.findByIdAndTenantIdAndDeletedAtIsNull(templateId, tenantId)
                .orElseThrow(() -> new NotFoundException("Template", templateId));
        boolean publishedOnly = Boolean.TRUE.equals(request.getPublishedOnly());
        TemplateContent source = resolveTemplateContent(tenantId, template, request.getVersionNumber(), publishedOnly);

        Map<String, Object> variables = request.getVariables() == null ? Map.of() : request.getVariables();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> tokenKeys = new LinkedHashSet<>();
        Set<String> dynamicSlots = new LinkedHashSet<>();

        String html = source.htmlContent();
        String text = source.textContent();
        String subject = source.subject();

        html = replaceSnippets(tenantId, html, errors);
        html = replaceBlocks(tenantId, html, publishedOnly, errors, warnings);
        DynamicReplacement htmlDynamic = replaceDynamicContent(tenantId, templateId, html, variables, false);
        html = htmlDynamic.content();
        dynamicSlots.addAll(htmlDynamic.slotKeys());
        warnings.addAll(htmlDynamic.warnings());

        text = replaceSnippets(tenantId, text, errors);
        DynamicReplacement textDynamic = replaceDynamicContent(tenantId, templateId, text, variables, true);
        text = textDynamic.content();
        dynamicSlots.addAll(textDynamic.slotKeys());
        warnings.addAll(textDynamic.warnings());

        BrandKit brandKit = resolveBrandKit(tenantId, request.getBrandKitId()).orElse(null);
        html = applyBrandKit(html, brandKit);
        subject = applyBrandTokens(subject, brandKit);
        text = applyBrandTokens(text, brandKit);

        PersonalizationTokenService.TokenRenderResult subjectTokens = tokenService.render(tenantId, subject, variables, false);
        PersonalizationTokenService.TokenRenderResult htmlTokens = tokenService.render(tenantId, html, variables, true);
        PersonalizationTokenService.TokenRenderResult textTokens = tokenService.render(tenantId, text, variables, false);
        subject = subjectTokens.content();
        html = htmlTokens.content();
        text = textTokens.content();
        tokenKeys.addAll(subjectTokens.tokenKeys());
        tokenKeys.addAll(htmlTokens.tokenKeys());
        tokenKeys.addAll(textTokens.tokenKeys());
        errors.addAll(subjectTokens.errors());
        errors.addAll(htmlTokens.errors());
        errors.addAll(textTokens.errors());
        warnings.addAll(subjectTokens.warnings());
        warnings.addAll(htmlTokens.warnings());
        warnings.addAll(textTokens.warnings());

        EmailStudioDto.ValidationResponse validation = validationService.validate(html, text);
        html = validation.getSanitizedHtml();
        text = validation.getTextContent();
        errors.addAll(validation.getErrors());
        warnings.addAll(validation.getWarnings());

        EmailStudioDto.RenderResponse response = new EmailStudioDto.RenderResponse();
        response.setSubject(subject);
        response.setHtmlContent(html);
        response.setTextContent(text);
        response.setValidationStatus(errors.isEmpty() ? "PASS" : "FAIL");
        response.setVersionNumber(source.versionNumber());
        response.setTokenKeys(tokenKeys.stream().toList());
        response.setDynamicSlots(dynamicSlots.stream().toList());
        response.setErrors(errors);
        response.setWarnings(warnings);
        response.setCompatibilityWarnings(validation.getCompatibilityWarnings());
        response.setMetrics(Map.of(
                "htmlBytes", html == null ? 0 : html.getBytes().length,
                "textBytes", text == null ? 0 : text.getBytes().length,
                "linkCount", validation.getLinkCount(),
                "imageCount", validation.getImageCount()
        ));
        return new RenderComputation(response);
    }

    public void requirePublishable(String tenantId, String templateId, Integer versionNumber) {
        EmailStudioDto.RenderRequest request = new EmailStudioDto.RenderRequest();
        request.setVersionNumber(versionNumber);
        request.setPublishedOnly(false);
        EmailStudioDto.RenderResponse response = render(tenantId, templateId, request);
        if (!response.getErrors().isEmpty()) {
            throw new ValidationException("content", "Template cannot be published until validation passes: " + String.join("; ", response.getErrors()));
        }
    }

    private TemplateContent resolveTemplateContent(String tenantId, EmailTemplate template, Integer versionNumber, boolean publishedOnly) {
        if (versionNumber != null) {
            TemplateVersion version = versionRepository.findByTemplate_IdAndVersionNumberAndTenantId(template.getId(), versionNumber, tenantId)
                    .orElseThrow(() -> new NotFoundException("TemplateVersion", template.getId() + " v" + versionNumber));
            if (publishedOnly && !Boolean.TRUE.equals(version.getIsPublished())) {
                throw new ValidationException("versionNumber", "Requested template version is not published");
            }
            return new TemplateContent(version.getVersionNumber(), nullToEmpty(version.getSubject()), nullToEmpty(version.getHtmlContent()), nullToEmpty(version.getTextContent()));
        }
        if (publishedOnly) {
            TemplateVersion version = versionRepository.findFirstByTemplate_IdAndTenantIdAndIsPublishedTrueOrderByVersionNumberDesc(template.getId(), tenantId)
                    .orElseThrow(() -> new NotFoundException("Published template version not found"));
            return new TemplateContent(version.getVersionNumber(), nullToEmpty(version.getSubject()), nullToEmpty(version.getHtmlContent()), nullToEmpty(version.getTextContent()));
        }
        String subject = template.getDraftSubject() != null ? template.getDraftSubject() : template.getSubject();
        String html = template.getDraftHtmlContent() != null ? template.getDraftHtmlContent() : template.getHtmlContent();
        String text = template.getDraftTextContent() != null ? template.getDraftTextContent() : template.getTextContent();
        Integer latestVersion = versionRepository.findFirstByTemplate_IdAndTenantIdOrderByVersionNumberDesc(template.getId(), tenantId)
                .map(TemplateVersion::getVersionNumber)
                .orElse(null);
        return new TemplateContent(latestVersion, nullToEmpty(subject), nullToEmpty(html), nullToEmpty(text));
    }

    private String replaceSnippets(String tenantId, String content, List<String> errors) {
        if (content == null || content.isBlank()) {
            return "";
        }
        Matcher matcher = SNIPPET_PATTERN.matcher(content);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            ContentSnippet snippet = snippetRepository.findByTenantIdAndSnippetKeyAndDeletedAtIsNull(tenantId, key).orElse(null);
            if (snippet == null) {
                errors.add("Unknown content snippet: " + key);
                matcher.appendReplacement(rendered, "");
                continue;
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(snippet.getContent()));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String replaceBlocks(String tenantId, String content, boolean publishedOnly, List<String> errors, List<String> warnings) {
        if (content == null || content.isBlank()) {
            return "";
        }
        Matcher matcher = BLOCK_PATTERN.matcher(content);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String blockId = matcher.group(1);
            ContentBlock block = blockRepository.findById(blockId)
                    .filter(candidate -> tenantId.equals(candidate.getTenantId()) && candidate.getDeletedAt() == null)
                    .orElse(null);
            if (block == null) {
                errors.add("Unknown content block: " + blockId);
                matcher.appendReplacement(rendered, "");
                continue;
            }
            Optional<ContentBlockVersion> published = blockVersionRepository
                    .findFirstByBlock_IdAndTenantIdAndIsPublishedTrueOrderByVersionNumberDesc(block.getId(), tenantId);
            if (published.isPresent()) {
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(published.get().getContent()));
                continue;
            }
            if (publishedOnly) {
                errors.add("Content block has no published version: " + block.getName());
                matcher.appendReplacement(rendered, "");
            } else {
                warnings.add("Using draft content block because no published version exists: " + block.getName());
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(block.getContent()));
            }
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private DynamicReplacement replaceDynamicContent(String tenantId, String templateId, String content, Map<String, Object> variables, boolean textContext) {
        if (content == null || content.isBlank()) {
            return new DynamicReplacement("", Set.of(), List.of());
        }
        Matcher matcher = DYNAMIC_PATTERN.matcher(content);
        StringBuffer rendered = new StringBuffer();
        Set<String> slots = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        while (matcher.find()) {
            String slotKey = matcher.group(1);
            slots.add(slotKey);
            DynamicContentRuleService.DynamicRuleRenderResult result =
                    dynamicRuleService.resolveSlot(tenantId, templateId, slotKey, variables);
            warnings.addAll(result.warnings());
            String replacement = textContext && result.textContent() != null && !result.textContent().isBlank()
                    ? result.textContent()
                    : result.htmlContent();
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return new DynamicReplacement(rendered.toString(), slots, warnings);
    }

    private Optional<BrandKit> resolveBrandKit(String tenantId, String brandKitId) {
        if (brandKitId != null && !brandKitId.isBlank()) {
            return brandKitRepository.findByIdAndTenantIdAndDeletedAtIsNull(brandKitId, tenantId);
        }
        return brandKitRepository.findFirstByTenantIdAndIsDefaultTrueAndDeletedAtIsNull(tenantId);
    }

    private String applyBrandKit(String html, BrandKit brandKit) {
        String branded = applyBrandTokens(html, brandKit);
        if (brandKit == null) {
            return branded;
        }
        if (brandKit.getFooterHtml() != null && !brandKit.getFooterHtml().isBlank() && !branded.contains("{{brand.footer}}")) {
            branded = branded + "\n" + brandKit.getFooterHtml();
        }
        if (brandKit.getLegalText() != null && !brandKit.getLegalText().isBlank() && !branded.contains("{{brand.legal}}")) {
            branded = branded + "\n<p style=\"font-size:12px;color:#6b7280\">" + validationService.escapeHtml(brandKit.getLegalText()) + "</p>";
        }
        return branded;
    }

    private String applyBrandTokens(String content, BrandKit brandKit) {
        if (content == null || content.isBlank()) {
            return "";
        }
        if (brandKit == null) {
            return content
                    .replace("{{brand.footer}}", "")
                    .replace("{{brand.legal}}", "")
                    .replace("{{brand.logoUrl}}", "")
                    .replace("{{brand.primaryColor}}", "")
                    .replace("{{brand.secondaryColor}}", "");
        }
        return content
                .replace("{{brand.footer}}", brandKit.getFooterHtml() == null ? "" : brandKit.getFooterHtml())
                .replace("{{brand.legal}}", brandKit.getLegalText() == null ? "" : validationService.escapeHtml(brandKit.getLegalText()))
                .replace("{{brand.logoUrl}}", brandKit.getLogoUrl() == null ? "" : validationService.escapeHtml(brandKit.getLogoUrl()))
                .replace("{{brand.primaryColor}}", brandKit.getPrimaryColor() == null ? "" : brandKit.getPrimaryColor())
                .replace("{{brand.secondaryColor}}", brandKit.getSecondaryColor() == null ? "" : brandKit.getSecondaryColor());
    }

    private void persistReport(String tenantId, String templateId, EmailStudioDto.ValidationResponse validation) {
        RenderValidationReport report = new RenderValidationReport();
        report.setTenantId(tenantId);
        report.setTemplateId(templateId);
        report.setStatus(validation.getStatus());
        try {
            report.setReportJson(objectMapper.writeValueAsString(validation));
        } catch (JsonProcessingException ex) {
            report.setReportJson("{\"status\":\"" + validation.getStatus() + "\"}");
        }
        reportRepository.save(report);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record TemplateContent(Integer versionNumber, String subject, String htmlContent, String textContent) {}

    private record DynamicReplacement(String content, Set<String> slotKeys, List<String> warnings) {}

    public record RenderComputation(EmailStudioDto.RenderResponse response) {}
}

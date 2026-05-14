package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import com.legent.content.domain.BrandKit;
import com.legent.content.domain.ContentSnippet;
import com.legent.content.domain.DynamicContentRule;
import com.legent.content.domain.LandingPage;
import com.legent.content.domain.PersonalizationToken;
import com.legent.content.domain.TemplateTestSendRecord;
import com.legent.content.dto.EmailStudioDto;
import com.legent.content.service.DynamicContentRuleService;
import com.legent.content.service.ContentBuilderService;
import com.legent.content.service.EmailContentValidationService;
import com.legent.content.service.EmailRenderService;
import com.legent.content.service.EmailStudioResourceService;
import com.legent.content.service.PersonalizationTokenService;
import com.legent.content.service.TemplateTestSendService;
import com.legent.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(AppConstants.API_BASE_PATH)
@RequiredArgsConstructor
public class EmailStudioController {

    private final EmailStudioResourceService resourceService;
    private final PersonalizationTokenService tokenService;
    private final DynamicContentRuleService dynamicRuleService;
    private final ContentBuilderService contentBuilderService;
    private final EmailRenderService renderService;
    private final TemplateTestSendService testSendService;
    private final EmailContentValidationService validationService;

    @PostMapping("/content/snippets")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EmailStudioDto.SnippetResponse> createSnippet(@Valid @RequestBody EmailStudioDto.SnippetRequest request) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(mapSnippet(resourceService.createSnippet(tenantId, request)));
    }

    @GetMapping("/content/snippets")
    public PagedResponse<EmailStudioDto.SnippetResponse> listSnippets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = TenantContext.requireTenantId();
        Page<ContentSnippet> snippets = resourceService.listSnippets(tenantId,
                PageRequest.of(page, Math.min(size, AppConstants.MAX_PAGE_SIZE)));
        return PagedResponse.of(snippets.getContent().stream().map(this::mapSnippet).toList(),
                page, size, snippets.getTotalElements(), snippets.getTotalPages());
    }

    @PutMapping("/content/snippets/{id}")
    public ApiResponse<EmailStudioDto.SnippetResponse> updateSnippet(
            @PathVariable String id,
            @Valid @RequestBody EmailStudioDto.SnippetRequest request) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(mapSnippet(resourceService.updateSnippet(tenantId, id, request)));
    }

    @DeleteMapping("/content/snippets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSnippet(@PathVariable String id) {
        resourceService.deleteSnippet(TenantContext.requireTenantId(), id);
    }

    @PostMapping("/personalization-tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EmailStudioDto.TokenResponse> createToken(@Valid @RequestBody EmailStudioDto.TokenRequest request) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(mapToken(tokenService.create(tenantId, request)));
    }

    @GetMapping("/personalization-tokens")
    public PagedResponse<EmailStudioDto.TokenResponse> listTokens(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        String tenantId = TenantContext.requireTenantId();
        Page<PersonalizationToken> tokens = tokenService.list(tenantId,
                PageRequest.of(page, Math.min(size, AppConstants.MAX_PAGE_SIZE)));
        return PagedResponse.of(tokens.getContent().stream().map(this::mapToken).toList(),
                page, size, tokens.getTotalElements(), tokens.getTotalPages());
    }

    @PutMapping("/personalization-tokens/{id}")
    public ApiResponse<EmailStudioDto.TokenResponse> updateToken(
            @PathVariable String id,
            @Valid @RequestBody EmailStudioDto.TokenRequest request) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(mapToken(tokenService.update(tenantId, id, request)));
    }

    @DeleteMapping("/personalization-tokens/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteToken(@PathVariable String id) {
        tokenService.delete(TenantContext.requireTenantId(), id);
    }

    @PostMapping("/templates/{templateId}/dynamic-content")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EmailStudioDto.DynamicRuleResponse> createDynamicRule(
            @PathVariable String templateId,
            @Valid @RequestBody EmailStudioDto.DynamicRuleRequest request) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(mapDynamicRule(dynamicRuleService.create(tenantId, templateId, request)));
    }

    @GetMapping("/templates/{templateId}/dynamic-content")
    public ApiResponse<List<EmailStudioDto.DynamicRuleResponse>> listDynamicRules(@PathVariable String templateId) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(dynamicRuleService.list(tenantId, templateId).stream().map(this::mapDynamicRule).toList());
    }

    @PostMapping("/content/builder/render")
    public ApiResponse<EmailStudioDto.BuilderLayoutResponse> renderBuilderLayout(
            @Valid @RequestBody EmailStudioDto.BuilderLayoutRequest request) {
        return ApiResponse.ok(contentBuilderService.renderLayout(request));
    }

    @PutMapping("/templates/{templateId}/dynamic-content/{id}")
    public ApiResponse<EmailStudioDto.DynamicRuleResponse> updateDynamicRule(
            @PathVariable String templateId,
            @PathVariable String id,
            @Valid @RequestBody EmailStudioDto.DynamicRuleRequest request) {
        String tenantId = TenantContext.requireTenantId();
        DynamicContentRule rule = dynamicRuleService.update(tenantId, id, request);
        return ApiResponse.ok(mapDynamicRule(rule));
    }

    @DeleteMapping("/templates/{templateId}/dynamic-content/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDynamicRule(@PathVariable String templateId, @PathVariable String id) {
        dynamicRuleService.delete(TenantContext.requireTenantId(), id);
    }

    @PostMapping("/brand-kits")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EmailStudioDto.BrandKitResponse> createBrandKit(@Valid @RequestBody EmailStudioDto.BrandKitRequest request) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(mapBrandKit(resourceService.createBrandKit(tenantId, request)));
    }

    @GetMapping("/brand-kits")
    public PagedResponse<EmailStudioDto.BrandKitResponse> listBrandKits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = TenantContext.requireTenantId();
        Page<BrandKit> brandKits = resourceService.listBrandKits(tenantId,
                PageRequest.of(page, Math.min(size, AppConstants.MAX_PAGE_SIZE)));
        return PagedResponse.of(brandKits.getContent().stream().map(this::mapBrandKit).toList(),
                page, size, brandKits.getTotalElements(), brandKits.getTotalPages());
    }

    @PutMapping("/brand-kits/{id}")
    public ApiResponse<EmailStudioDto.BrandKitResponse> updateBrandKit(
            @PathVariable String id,
            @Valid @RequestBody EmailStudioDto.BrandKitRequest request) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(mapBrandKit(resourceService.updateBrandKit(tenantId, id, request)));
    }

    @DeleteMapping("/brand-kits/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBrandKit(@PathVariable String id) {
        resourceService.deleteBrandKit(TenantContext.requireTenantId(), id);
    }

    @PostMapping("/landing-pages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('content:write', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public ApiResponse<EmailStudioDto.LandingPageResponse> createLandingPage(@Valid @RequestBody EmailStudioDto.LandingPageRequest request) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(mapLandingPage(resourceService.createLandingPage(tenantId, request)));
    }

    @GetMapping("/landing-pages")
    public PagedResponse<EmailStudioDto.LandingPageResponse> listLandingPages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = TenantContext.requireTenantId();
        Page<LandingPage> pages = resourceService.listLandingPages(tenantId,
                PageRequest.of(page, Math.min(size, AppConstants.MAX_PAGE_SIZE)));
        return PagedResponse.of(pages.getContent().stream().map(this::mapLandingPage).toList(),
                page, size, pages.getTotalElements(), pages.getTotalPages());
    }

    @PutMapping("/landing-pages/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('content:write', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public ApiResponse<EmailStudioDto.LandingPageResponse> updateLandingPage(
            @PathVariable String id,
            @Valid @RequestBody EmailStudioDto.LandingPageRequest request) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(mapLandingPage(resourceService.updateLandingPage(tenantId, id, request)));
    }

    @PostMapping("/landing-pages/{id}/publish")
    @PreAuthorize("@rbacEvaluator.hasPermission('content:publish', principal.roles) or @rbacEvaluator.hasPermission('content:*', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public ApiResponse<EmailStudioDto.LandingPageResponse> publishLandingPage(@PathVariable String id) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(mapLandingPage(resourceService.publishLandingPage(tenantId, id)));
    }

    @PostMapping("/landing-pages/{id}/archive")
    @PreAuthorize("@rbacEvaluator.hasPermission('content:publish', principal.roles) or @rbacEvaluator.hasPermission('content:*', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public ApiResponse<EmailStudioDto.LandingPageResponse> archiveLandingPage(@PathVariable String id) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(mapLandingPage(resourceService.archiveLandingPage(tenantId, id)));
    }

    @DeleteMapping("/landing-pages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacEvaluator.hasPermission('content:delete', principal.roles) or @rbacEvaluator.hasPermission('content:*', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public void deleteLandingPage(@PathVariable String id) {
        resourceService.deleteLandingPage(TenantContext.requireTenantId(), id);
    }

    @GetMapping("/public/landing-pages/{slug}")
    public ApiResponse<EmailStudioDto.LandingPageResponse> getPublicLandingPage(@PathVariable String slug) {
        return ApiResponse.ok(mapPublicLandingPage(resourceService.getPublishedLandingPage(slug)));
    }

    @PostMapping("/templates/{templateId}/render")
    public ApiResponse<EmailStudioDto.RenderResponse> renderTemplate(
            @PathVariable String templateId,
            @RequestBody(required = false) EmailStudioDto.RenderRequest request) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(renderService.render(tenantId, templateId, request));
    }

    @PostMapping("/templates/{templateId}/validate")
    public ApiResponse<EmailStudioDto.ValidationResponse> validateTemplate(
            @PathVariable String templateId,
            @RequestBody(required = false) EmailStudioDto.RenderRequest request) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(renderService.validateAndPersist(tenantId, templateId, request));
    }

    @PostMapping("/templates/{templateId}/test-sends")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EmailStudioDto.TestSendRecordResponse> createTestSend(
            @PathVariable String templateId,
            @Valid @RequestBody EmailStudioDto.TestSendRequest request) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(mapTestSend(testSendService.send(tenantId, templateId, request)));
    }

    @GetMapping("/templates/{templateId}/test-sends")
    public ApiResponse<List<EmailStudioDto.TestSendRecordResponse>> listTestSends(@PathVariable String templateId) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(testSendService.list(tenantId, templateId).stream().map(this::mapTestSend).toList());
    }

    @PostMapping("/templates/{templateId}/test-send-matrix")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EmailStudioDto.TestSendMatrixResponse> createTestSendMatrix(
            @PathVariable String templateId,
            @Valid @RequestBody EmailStudioDto.TestSendMatrixRequest request) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(contentBuilderService.sendMatrix(tenantId, templateId, request));
    }

    private EmailStudioDto.SnippetResponse mapSnippet(ContentSnippet snippet) {
        EmailStudioDto.SnippetResponse response = new EmailStudioDto.SnippetResponse();
        response.setId(snippet.getId());
        response.setSnippetKey(snippet.getSnippetKey());
        response.setName(snippet.getName());
        response.setSnippetType(snippet.getSnippetType());
        response.setContent(snippet.getContent());
        response.setDescription(snippet.getDescription());
        response.setIsGlobal(snippet.getIsGlobal());
        response.setCreatedAt(snippet.getCreatedAt() != null ? snippet.getCreatedAt().toString() : null);
        response.setUpdatedAt(snippet.getUpdatedAt() != null ? snippet.getUpdatedAt().toString() : null);
        return response;
    }

    private EmailStudioDto.TokenResponse mapToken(PersonalizationToken token) {
        EmailStudioDto.TokenResponse response = new EmailStudioDto.TokenResponse();
        response.setId(token.getId());
        response.setTokenKey(token.getTokenKey());
        response.setDisplayName(token.getDisplayName());
        response.setDescription(token.getDescription());
        response.setSourceType(token.getSourceType());
        response.setDataPath(token.getDataPath());
        response.setDefaultValue(token.getDefaultValue());
        response.setSampleValue(token.getSampleValue());
        response.setRequired(token.getRequired());
        response.setCreatedAt(token.getCreatedAt() != null ? token.getCreatedAt().toString() : null);
        response.setUpdatedAt(token.getUpdatedAt() != null ? token.getUpdatedAt().toString() : null);
        return response;
    }

    private EmailStudioDto.DynamicRuleResponse mapDynamicRule(DynamicContentRule rule) {
        EmailStudioDto.DynamicRuleResponse response = new EmailStudioDto.DynamicRuleResponse();
        response.setId(rule.getId());
        response.setTemplateId(rule.getTemplateId());
        response.setSlotKey(rule.getSlotKey());
        response.setName(rule.getName());
        response.setPriority(rule.getPriority());
        response.setConditionField(rule.getConditionField());
        response.setOperator(rule.getOperator());
        response.setConditionValue(rule.getConditionValue());
        response.setHtmlContent(rule.getHtmlContent());
        response.setTextContent(rule.getTextContent());
        response.setActive(rule.getActive());
        response.setCreatedAt(rule.getCreatedAt() != null ? rule.getCreatedAt().toString() : null);
        response.setUpdatedAt(rule.getUpdatedAt() != null ? rule.getUpdatedAt().toString() : null);
        return response;
    }

    private EmailStudioDto.BrandKitResponse mapBrandKit(BrandKit brandKit) {
        EmailStudioDto.BrandKitResponse response = new EmailStudioDto.BrandKitResponse();
        response.setId(brandKit.getId());
        response.setName(brandKit.getName());
        response.setLogoUrl(brandKit.getLogoUrl());
        response.setPrimaryColor(brandKit.getPrimaryColor());
        response.setSecondaryColor(brandKit.getSecondaryColor());
        response.setFontFamily(brandKit.getFontFamily());
        response.setFooterHtml(brandKit.getFooterHtml());
        response.setLegalText(brandKit.getLegalText());
        response.setDefaultFromName(brandKit.getDefaultFromName());
        response.setDefaultFromEmail(brandKit.getDefaultFromEmail());
        response.setIsDefault(brandKit.getIsDefault());
        response.setCreatedAt(brandKit.getCreatedAt() != null ? brandKit.getCreatedAt().toString() : null);
        response.setUpdatedAt(brandKit.getUpdatedAt() != null ? brandKit.getUpdatedAt().toString() : null);
        return response;
    }

    private EmailStudioDto.LandingPageResponse mapLandingPage(LandingPage landingPage) {
        EmailStudioDto.LandingPageResponse response = new EmailStudioDto.LandingPageResponse();
        response.setId(landingPage.getId());
        response.setName(landingPage.getName());
        response.setSlug(landingPage.getSlug());
        response.setStatus(landingPage.getStatus() != null ? landingPage.getStatus().name() : null);
        response.setHtmlContent(landingPage.getHtmlContent());
        response.setMetadata(landingPage.getMetadata());
        response.setPublishedAt(landingPage.getPublishedAt() != null ? landingPage.getPublishedAt().toString() : null);
        response.setCreatedAt(landingPage.getCreatedAt() != null ? landingPage.getCreatedAt().toString() : null);
        response.setUpdatedAt(landingPage.getUpdatedAt() != null ? landingPage.getUpdatedAt().toString() : null);
        return response;
    }

    private EmailStudioDto.LandingPageResponse mapPublicLandingPage(LandingPage landingPage) {
        EmailStudioDto.LandingPageResponse response = mapLandingPage(landingPage);
        response.setHtmlContent(validationService.sanitizeLandingPage(response.getHtmlContent()));
        return response;
    }

    private EmailStudioDto.TestSendRecordResponse mapTestSend(TemplateTestSendRecord record) {
        EmailStudioDto.TestSendRecordResponse response = new EmailStudioDto.TestSendRecordResponse();
        response.setId(record.getId());
        response.setTemplateId(record.getTemplateId());
        response.setRecipientEmail(record.getRecipientEmail());
        response.setRecipientGroup(record.getRecipientGroup());
        response.setSubject(record.getSubject());
        response.setStatus(record.getStatus());
        response.setMessageId(record.getMessageId());
        response.setVariablesJson(record.getVariablesJson());
        response.setErrorMessage(record.getErrorMessage());
        response.setCreatedAt(record.getCreatedAt() != null ? record.getCreatedAt().toString() : null);
        response.setUpdatedAt(record.getUpdatedAt() != null ? record.getUpdatedAt().toString() : null);
        return response;
    }
}

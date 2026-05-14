package com.legent.content.service;

import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.content.domain.BrandKit;
import com.legent.content.domain.ContentSnippet;
import com.legent.content.domain.LandingPage;
import com.legent.content.dto.EmailStudioDto;
import com.legent.content.repository.BrandKitRepository;
import com.legent.content.repository.ContentSnippetRepository;
import com.legent.content.repository.LandingPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EmailStudioResourceService {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_.-]{0,127}$");
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,120}[a-z0-9]$");
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$");

    private final ContentSnippetRepository snippetRepository;
    private final BrandKitRepository brandKitRepository;
    private final LandingPageRepository landingPageRepository;
    private final EmailContentValidationService validationService;

    @Transactional
    public ContentSnippet createSnippet(String tenantId, EmailStudioDto.SnippetRequest request) {
        validateKey("snippetKey", request.getSnippetKey());
        if (snippetRepository.existsByTenantIdAndSnippetKeyAndDeletedAtIsNull(tenantId, request.getSnippetKey())) {
            throw new ConflictException("Content snippet already exists: " + request.getSnippetKey());
        }
        ContentSnippet snippet = new ContentSnippet();
        snippet.setTenantId(tenantId);
        applySnippet(snippet, request);
        return snippetRepository.save(snippet);
    }

    @Transactional
    public ContentSnippet updateSnippet(String tenantId, String id, EmailStudioDto.SnippetRequest request) {
        ContentSnippet snippet = getSnippet(tenantId, id);
        if (request.getSnippetKey() != null && !request.getSnippetKey().equals(snippet.getSnippetKey())) {
            validateKey("snippetKey", request.getSnippetKey());
            if (snippetRepository.existsByTenantIdAndSnippetKeyAndDeletedAtIsNull(tenantId, request.getSnippetKey())) {
                throw new ConflictException("Content snippet already exists: " + request.getSnippetKey());
            }
        }
        applySnippet(snippet, request);
        return snippetRepository.save(snippet);
    }

    @Transactional(readOnly = true)
    public ContentSnippet getSnippet(String tenantId, String id) {
        return snippetRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new NotFoundException("ContentSnippet", id));
    }

    @Transactional(readOnly = true)
    public Page<ContentSnippet> listSnippets(String tenantId, Pageable pageable) {
        return snippetRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable);
    }

    @Transactional
    public void deleteSnippet(String tenantId, String id) {
        ContentSnippet snippet = getSnippet(tenantId, id);
        snippet.setDeletedAt(Instant.now());
        snippetRepository.save(snippet);
    }

    @Transactional
    public BrandKit createBrandKit(String tenantId, EmailStudioDto.BrandKitRequest request) {
        if (brandKitRepository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, request.getName())) {
            throw new ConflictException("Brand kit already exists: " + request.getName());
        }
        BrandKit brandKit = new BrandKit();
        brandKit.setTenantId(tenantId);
        applyBrandKit(brandKit, request);
        if (Boolean.TRUE.equals(brandKit.getIsDefault())) {
            clearDefaultBrandKits(tenantId);
        }
        return brandKitRepository.save(brandKit);
    }

    @Transactional
    public BrandKit updateBrandKit(String tenantId, String id, EmailStudioDto.BrandKitRequest request) {
        BrandKit brandKit = getBrandKit(tenantId, id);
        if (request.getName() != null && !request.getName().equals(brandKit.getName())
                && brandKitRepository.existsByTenantIdAndNameAndDeletedAtIsNull(tenantId, request.getName())) {
            throw new ConflictException("Brand kit already exists: " + request.getName());
        }
        applyBrandKit(brandKit, request);
        if (Boolean.TRUE.equals(brandKit.getIsDefault())) {
            clearDefaultBrandKits(tenantId, id);
        }
        return brandKitRepository.save(brandKit);
    }

    @Transactional(readOnly = true)
    public BrandKit getBrandKit(String tenantId, String id) {
        return brandKitRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new NotFoundException("BrandKit", id));
    }

    @Transactional(readOnly = true)
    public Page<BrandKit> listBrandKits(String tenantId, Pageable pageable) {
        return brandKitRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable);
    }

    @Transactional
    public void deleteBrandKit(String tenantId, String id) {
        BrandKit brandKit = getBrandKit(tenantId, id);
        brandKit.setDeletedAt(Instant.now());
        brandKitRepository.save(brandKit);
    }

    @Transactional
    public LandingPage createLandingPage(String tenantId, EmailStudioDto.LandingPageRequest request) {
        validateSlug(request.getSlug());
        if (landingPageRepository.existsBySlugAndDeletedAtIsNull(request.getSlug())) {
            throw new ConflictException("Landing page slug already exists: " + request.getSlug());
        }
        LandingPage landingPage = new LandingPage();
        landingPage.setTenantId(tenantId);
        applyLandingPage(landingPage, request);
        return landingPageRepository.save(landingPage);
    }

    @Transactional
    public LandingPage updateLandingPage(String tenantId, String id, EmailStudioDto.LandingPageRequest request) {
        LandingPage landingPage = getLandingPage(tenantId, id);
        if (request.getSlug() != null && !request.getSlug().equals(landingPage.getSlug())) {
            validateSlug(request.getSlug());
            if (landingPageRepository.existsBySlugAndDeletedAtIsNull(request.getSlug())) {
                throw new ConflictException("Landing page slug already exists: " + request.getSlug());
            }
        }
        applyLandingPage(landingPage, request);
        return landingPageRepository.save(landingPage);
    }

    @Transactional(readOnly = true)
    public LandingPage getLandingPage(String tenantId, String id) {
        return landingPageRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new NotFoundException("LandingPage", id));
    }

    @Transactional(readOnly = true)
    public Page<LandingPage> listLandingPages(String tenantId, Pageable pageable) {
        return landingPageRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public LandingPage getPublishedLandingPage(String slug) {
        return landingPageRepository.findFirstBySlugAndStatusAndDeletedAtIsNull(slug, LandingPage.Status.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("LandingPage", slug));
    }

    @Transactional
    public LandingPage publishLandingPage(String tenantId, String id) {
        LandingPage landingPage = getLandingPage(tenantId, id);
        landingPage.setHtmlContent(validationService.sanitizeLandingPage(landingPage.getHtmlContent()));
        landingPage.setStatus(LandingPage.Status.PUBLISHED);
        landingPage.setPublishedAt(Instant.now());
        return landingPageRepository.save(landingPage);
    }

    @Transactional
    public LandingPage archiveLandingPage(String tenantId, String id) {
        LandingPage landingPage = getLandingPage(tenantId, id);
        landingPage.setStatus(LandingPage.Status.ARCHIVED);
        return landingPageRepository.save(landingPage);
    }

    @Transactional
    public void deleteLandingPage(String tenantId, String id) {
        LandingPage landingPage = getLandingPage(tenantId, id);
        landingPage.setDeletedAt(Instant.now());
        landingPageRepository.save(landingPage);
    }

    private void applySnippet(ContentSnippet snippet, EmailStudioDto.SnippetRequest request) {
        if (request.getSnippetKey() != null) snippet.setSnippetKey(request.getSnippetKey().trim());
        if (request.getName() != null) snippet.setName(request.getName().trim());
        if (request.getSnippetType() != null) snippet.setSnippetType(request.getSnippetType().trim().toUpperCase(Locale.ROOT));
        if (request.getContent() != null) snippet.setContent(request.getContent());
        if (request.getDescription() != null) snippet.setDescription(request.getDescription());
        if (request.getIsGlobal() != null) snippet.setIsGlobal(request.getIsGlobal());
    }

    private void applyBrandKit(BrandKit brandKit, EmailStudioDto.BrandKitRequest request) {
        if (request.getName() != null) brandKit.setName(request.getName().trim());
        if (request.getLogoUrl() != null) brandKit.setLogoUrl(request.getLogoUrl());
        if (request.getPrimaryColor() != null) brandKit.setPrimaryColor(validateColor("primaryColor", request.getPrimaryColor()));
        if (request.getSecondaryColor() != null) brandKit.setSecondaryColor(validateColor("secondaryColor", request.getSecondaryColor()));
        if (request.getFontFamily() != null) brandKit.setFontFamily(request.getFontFamily());
        if (request.getFooterHtml() != null) brandKit.setFooterHtml(validationService.sanitize(request.getFooterHtml()));
        if (request.getLegalText() != null) brandKit.setLegalText(request.getLegalText());
        if (request.getDefaultFromName() != null) brandKit.setDefaultFromName(request.getDefaultFromName());
        if (request.getDefaultFromEmail() != null) brandKit.setDefaultFromEmail(request.getDefaultFromEmail());
        if (request.getIsDefault() != null) brandKit.setIsDefault(request.getIsDefault());
    }

    private void applyLandingPage(LandingPage landingPage, EmailStudioDto.LandingPageRequest request) {
        boolean shouldPublish = Boolean.TRUE.equals(request.getPublish());
        boolean alreadyPublished = LandingPage.Status.PUBLISHED.equals(landingPage.getStatus());
        if (request.getName() != null) landingPage.setName(request.getName().trim());
        if (request.getSlug() != null) landingPage.setSlug(request.getSlug().trim().toLowerCase(Locale.ROOT));
        if (request.getHtmlContent() != null) landingPage.setHtmlContent(request.getHtmlContent());
        if (request.getMetadata() != null) landingPage.setMetadata(request.getMetadata());
        if (shouldPublish || alreadyPublished) {
            landingPage.setHtmlContent(validationService.sanitizeLandingPage(landingPage.getHtmlContent()));
        }
        if (shouldPublish) {
            landingPage.setStatus(LandingPage.Status.PUBLISHED);
            landingPage.setPublishedAt(Instant.now());
        }
    }

    private void clearDefaultBrandKits(String tenantId) {
        clearDefaultBrandKits(tenantId, null);
    }

    private void clearDefaultBrandKits(String tenantId, String exceptId) {
        List<BrandKit> brandKits = brandKitRepository.findByTenantIdAndDeletedAtIsNull(tenantId);
        for (BrandKit current : brandKits) {
            if ((exceptId == null || !exceptId.equals(current.getId())) && Boolean.TRUE.equals(current.getIsDefault())) {
                current.setIsDefault(false);
                brandKitRepository.save(current);
            }
        }
    }

    private void validateKey(String field, String value) {
        if (value == null || value.isBlank() || !KEY_PATTERN.matcher(value).matches()) {
            throw new ValidationException(field, "Use letters, numbers, dots, underscores, and dashes; key must start with a letter");
        }
    }

    private void validateSlug(String slug) {
        if (slug == null || slug.isBlank() || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new ValidationException("slug", "Use lowercase letters, numbers, and dashes; length must be 3-122 characters");
        }
    }

    private String validateColor(String field, String color) {
        if (color == null || color.isBlank()) {
            return null;
        }
        String trimmed = color.trim();
        if (!HEX_COLOR_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException(field, "Use a valid hex color such as #1F6FEB");
        }
        return trimmed;
    }
}

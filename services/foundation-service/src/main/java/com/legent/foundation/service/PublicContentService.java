package com.legent.foundation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.domain.PublicContent;
import com.legent.foundation.dto.PublicContentDto;
import com.legent.foundation.repository.PublicContentRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicContentService {

    private final PublicContentRepository publicContentRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PublicContentDto.Response getPublishedPage(String tenantId, String workspaceId, String pageKey) {
        String normalizedPage = normalize(pageKey);
        if (normalizedPage == null) {
            throw new IllegalArgumentException("pageKey is required");
        }
        Optional<PublicContent> found = publicContentRepository
                .findByTenantIdAndWorkspaceIdAndContentTypeAndPageKeyAndStatus(
                        normalize(tenantId),
                        normalize(workspaceId),
                        "PAGE",
                        normalizedPage,
                        "PUBLISHED");
        if (found.isPresent()) {
            return toResponse(found.get());
        }
        return defaultPage(normalizedPage);
    }

    @Transactional(readOnly = true)
    public List<PublicContentDto.Response> listPublishedBlog(String tenantId, String workspaceId) {
        List<PublicContent> records = publicContentRepository
                .findByTenantIdAndWorkspaceIdAndContentTypeAndStatusOrderByPublishedAtDesc(
                        normalize(tenantId),
                        normalize(workspaceId),
                        "BLOG",
                        "PUBLISHED");
        List<PublicContentDto.Response> response = new ArrayList<>();
        for (PublicContent record : records) {
            response.add(toResponse(record));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public PublicContentDto.Response getPublishedBlogBySlug(String tenantId, String workspaceId, String slug) {
        PublicContent content = publicContentRepository
                .findByTenantIdAndWorkspaceIdAndContentTypeAndSlugAndStatus(
                        normalize(tenantId),
                        normalize(workspaceId),
                        "BLOG",
                        normalize(slug),
                        "PUBLISHED")
                .orElseThrow(() -> new IllegalArgumentException("Blog content not found"));
        return toResponse(content);
    }

    @Transactional(readOnly = true)
    public List<PublicContentDto.Response> listAdminContent() {
        String tenantId = normalize(TenantContext.getTenantId());
        String workspaceId = normalize(TenantContext.getWorkspaceId());
        List<PublicContent> records = publicContentRepository.findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(tenantId, workspaceId);
        List<PublicContentDto.Response> response = new ArrayList<>();
        for (PublicContent record : records) {
            response.add(toResponse(record));
        }
        return response;
    }

    @Transactional
    public PublicContentDto.Response upsert(PublicContentDto.UpsertRequest request, String id) {
        String tenantId = normalize(TenantContext.getTenantId());
        String workspaceId = normalize(TenantContext.getWorkspaceId());
        String type = normalizeUpper(request.getContentType());
        String pageKey = normalize(request.getPageKey());
        String slug = normalize(request.getSlug());
        if (type == null || pageKey == null) {
            throw new IllegalArgumentException("contentType and pageKey are required");
        }

        PublicContent record = (id != null && !id.isBlank())
                ? publicContentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Public content not found"))
                : publicContentRepository
                    .findByTenantIdAndWorkspaceIdAndContentTypeAndPageKeyAndSlug(tenantId, workspaceId, type, pageKey, slug)
                    .orElseGet(PublicContent::new);

        record.setTenantId(tenantId);
        record.setWorkspaceId(workspaceId);
        record.setContentType(type);
        record.setPageKey(pageKey);
        record.setSlug(slug);
        record.setTitle(request.getTitle());
        record.setPayload(toJson(request.getPayload() == null ? Map.of() : request.getPayload()));
        record.setSeoMeta(toJson(request.getSeoMeta() == null ? Map.of() : request.getSeoMeta()));
        String status = normalizeUpper(request.getStatus());
        record.setStatus(status == null ? "DRAFT" : status);
        if ("PUBLISHED".equals(record.getStatus())) {
            record.setPublishedAt(Instant.now());
        } else if (!"PUBLISHED".equals(record.getStatus())) {
            record.setPublishedAt(null);
        }

        return toResponse(publicContentRepository.save(record));
    }

    @Transactional
    public PublicContentDto.Response publish(String id, boolean published) {
        PublicContent record = publicContentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Public content not found"));
        record.setStatus(published ? "PUBLISHED" : "DRAFT");
        record.setPublishedAt(published ? Instant.now() : null);
        return toResponse(publicContentRepository.save(record));
    }

    private PublicContentDto.Response toResponse(PublicContent record) {
        return PublicContentDto.Response.builder()
                .id(record.getId())
                .tenantId(record.getTenantId())
                .workspaceId(record.getWorkspaceId())
                .contentType(record.getContentType())
                .pageKey(record.getPageKey())
                .slug(record.getSlug())
                .title(record.getTitle())
                .status(record.getStatus())
                .payload(parseJson(record.getPayload()))
                .seoMeta(parseJson(record.getSeoMeta()))
                .publishedAt(record.getPublishedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private PublicContentDto.Response defaultPage(String pageKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("heroTitle", "Email Marketing That Feels Effortless");
        payload.put("heroSubtitle", "Audience intelligence, campaign orchestration, automation, delivery and analytics in one premium studio.");
        payload.put("ctaPrimary", "Start Free");
        payload.put("ctaSecondary", "Book Demo");
        payload.put("modules", List.of("Audience Studio", "Template Studio", "Campaign Studio", "Automation Studio", "Delivery Studio", "Analytics Studio"));
        payload.put("pageKey", pageKey);
        return PublicContentDto.Response.builder()
                .id("default-" + pageKey)
                .contentType("PAGE")
                .pageKey(pageKey)
                .status("PUBLISHED")
                .title(pageKey.substring(0, 1).toUpperCase(Locale.ROOT) + pageKey.substring(1))
                .payload(payload)
                .seoMeta(Map.of("title", "Legent " + pageKey, "description", "Legent marketing platform"))
                .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize content payload", e);
        }
    }

    private Map<String, Object> parseJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            log.warn("Failed to parse public content payload: {}", ex.getMessage());
            return Map.of();
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeUpper(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}


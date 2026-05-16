package com.legent.campaign.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.cache.service.CacheService;
import com.legent.common.event.EmailContentReference;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RenderedContentReferenceService {

    private static final String CACHE_KEY_PREFIX = "email:content:";
    private static final String STORAGE_BACKEND = "redis";

    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    @Value("${legent.campaign.send.content-reference-ttl:PT72H}")
    private Duration contentReferenceTtl;

    public EmailContentReference createReference(CreateRequest request, boolean inlineFallbackIncluded) {
        validate(request);
        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plus(contentReferenceTtl);
        String referenceId = referenceId(request);

        Map<String, String> content = new LinkedHashMap<>();
        content.put("tenantId", request.tenantId());
        content.put("workspaceId", request.workspaceId());
        content.put("campaignId", request.campaignId());
        content.put("jobId", request.jobId());
        content.put("batchId", request.batchId());
        content.put("messageId", request.messageId());
        content.put("contentId", request.contentId());
        content.put("subject", request.subject());
        content.put("htmlBody", request.htmlBody());
        if (request.textBody() != null && !request.textBody().isBlank()) {
            content.put("textBody", request.textBody());
        }

        try {
            cacheService.set(cacheKey(referenceId), objectMapper.writeValueAsString(content), contentReferenceTtl);
        } catch (JsonProcessingException e) {
            throw new ValidationException("contentReference", "Unable to serialize rendered content reference");
        }

        return EmailContentReference.builder()
                .referenceId(referenceId)
                .storageBackend(STORAGE_BACKEND)
                .tenantId(request.tenantId())
                .workspaceId(request.workspaceId())
                .campaignId(request.campaignId())
                .jobId(request.jobId())
                .batchId(request.batchId())
                .messageId(request.messageId())
                .contentId(request.contentId())
                .subjectSha256(sha256Hex(request.subject()))
                .htmlSha256(sha256Hex(request.htmlBody()))
                .textSha256(request.textBody() == null || request.textBody().isBlank() ? null : sha256Hex(request.textBody()))
                .subjectBytes(byteLength(request.subject()))
                .htmlBytes(byteLength(request.htmlBody()))
                .textBytes(request.textBody() == null || request.textBody().isBlank() ? 0 : byteLength(request.textBody()))
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .inlineFallbackIncluded(inlineFallbackIncluded)
                .build();
    }

    public StoredRenderedContent readReference(String tenantId, String workspaceId, String referenceId) {
        require(tenantId, "tenantId");
        require(workspaceId, "workspaceId");
        if (isBlank(referenceId)) {
            throw new ValidationException("contentReference", "contentReference is required");
        }
        String serialized = cacheService.get(cacheKey(referenceId), String.class)
                .orElseThrow(() -> new NotFoundException("Rendered content reference not found"));
        try {
            Map<String, String> content = objectMapper.readValue(serialized, new TypeReference<>() {});
            if (!tenantId.equals(content.get("tenantId")) || !workspaceId.equals(content.get("workspaceId"))) {
                throw new NotFoundException("Rendered content reference not found");
            }
            return new StoredRenderedContent(
                    content.get("subject"),
                    content.get("htmlBody"),
                    content.get("textBody"),
                    content);
        } catch (JsonProcessingException e) {
            throw new ValidationException("contentReference", "Rendered content reference is not readable");
        }
    }

    private String referenceId(CreateRequest request) {
        String seed = String.join(":",
                request.tenantId(),
                request.workspaceId(),
                request.campaignId(),
                request.jobId(),
                request.batchId(),
                request.messageId(),
                request.contentId());
        return "cr_" + sha256Hex(seed).substring(0, 32);
    }

    private String cacheKey(String referenceId) {
        return CACHE_KEY_PREFIX + referenceId;
    }

    private void validate(CreateRequest request) {
        if (request == null) {
            throw new ValidationException("contentReference", "Rendered content reference request is required");
        }
        require(request.tenantId(), "tenantId");
        require(request.workspaceId(), "workspaceId");
        require(request.campaignId(), "campaignId");
        require(request.jobId(), "jobId");
        require(request.batchId(), "batchId");
        require(request.messageId(), "messageId");
        require(request.contentId(), "contentId");
        require(request.subject(), "subject");
        require(request.htmlBody(), "htmlBody");
    }

    private void require(String value, String field) {
        if (isBlank(value)) {
            throw new ValidationException(field, field + " is required for rendered content reference");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int byteLength(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public record CreateRequest(String tenantId,
                                String workspaceId,
                                String campaignId,
                                String jobId,
                                String batchId,
                                String messageId,
                                String contentId,
                                String subject,
                                String htmlBody,
                                String textBody) {
    }

    public record StoredRenderedContent(String subject,
                                        String htmlBody,
                                        String textBody,
                                        Map<String, String> metadata) {
    }
}

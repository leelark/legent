package com.legent.content.service;

import com.legent.common.event.EmailContentReference;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.content.domain.RenderedContentSnapshot;
import com.legent.content.repository.RenderedContentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RenderedContentSnapshotService {

    private static final String STORAGE_BACKEND = "content-service";

    private final RenderedContentSnapshotRepository repository;

    @Value("${legent.content.rendered-snapshot-ttl:PT72H}")
    private Duration snapshotTtl;

    @Transactional
    public EmailContentReference create(String tenantId,
                                        String workspaceId,
                                        SnapshotRequest request,
                                        boolean inlineFallbackIncluded) {
        String scopedTenantId = require(tenantId, "tenantId");
        String scopedWorkspaceId = require(workspaceId, "workspaceId");
        validateScope(scopedTenantId, scopedWorkspaceId, request);
        validate(request);

        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plus(effectiveSnapshotTtl());
        String referenceId = referenceId(scopedTenantId, scopedWorkspaceId, request);

        RenderedContentSnapshot snapshot = repository
                .findByTenantIdAndWorkspaceIdAndReferenceIdAndDeletedAtIsNull(scopedTenantId, scopedWorkspaceId, referenceId)
                .orElseGet(RenderedContentSnapshot::new);
        snapshot.setTenantId(scopedTenantId);
        if (snapshot.getCreatedAt() == null) {
            snapshot.setCreatedAt(createdAt);
        }
        snapshot.setWorkspaceId(scopedWorkspaceId);
        snapshot.setReferenceId(referenceId);
        snapshot.setCampaignId(request.campaignId());
        snapshot.setJobId(request.jobId());
        snapshot.setBatchId(request.batchId());
        snapshot.setMessageId(request.messageId());
        snapshot.setContentId(request.contentId());
        snapshot.setSubject(request.subject());
        snapshot.setHtmlBody(request.htmlBody());
        snapshot.setTextBody(blankToNull(request.textBody()));
        snapshot.setSubjectSha256(sha256Hex(request.subject()));
        snapshot.setHtmlSha256(sha256Hex(request.htmlBody()));
        snapshot.setTextSha256(isBlank(request.textBody()) ? null : sha256Hex(request.textBody()));
        snapshot.setSubjectBytes(byteLength(request.subject()));
        snapshot.setHtmlBytes(byteLength(request.htmlBody()));
        snapshot.setTextBytes(isBlank(request.textBody()) ? 0 : byteLength(request.textBody()));
        snapshot.setInlineFallbackIncluded(inlineFallbackIncluded);
        snapshot.setExpiresAt(expiresAt);

        RenderedContentSnapshot saved = repository.save(snapshot);
        return toReference(saved);
    }

    @Transactional(readOnly = true)
    public StoredRenderedContent read(String tenantId, String workspaceId, String referenceId) {
        String scopedTenantId = require(tenantId, "tenantId");
        String scopedWorkspaceId = require(workspaceId, "workspaceId");
        String scopedReferenceId = require(referenceId, "referenceId");
        RenderedContentSnapshot snapshot = repository
                .findByTenantIdAndWorkspaceIdAndReferenceIdAndDeletedAtIsNull(
                        scopedTenantId,
                        scopedWorkspaceId,
                        scopedReferenceId)
                .orElseThrow(() -> new NotFoundException("Rendered content reference not found"));
        if (snapshot.getExpiresAt() == null || !snapshot.getExpiresAt().isAfter(Instant.now())) {
            throw new NotFoundException("Rendered content reference not found");
        }
        return toStored(snapshot);
    }

    @Scheduled(fixedDelayString = "${legent.content.rendered-snapshot-purge-interval-ms:3600000}")
    public void purgeExpiredSnapshotsOnSchedule() {
        purgeExpiredSnapshots();
    }

    @Transactional
    public int purgeExpiredSnapshots() {
        return repository.deleteExpired(Instant.now());
    }

    private EmailContentReference toReference(RenderedContentSnapshot snapshot) {
        return EmailContentReference.builder()
                .referenceId(snapshot.getReferenceId())
                .storageBackend(STORAGE_BACKEND)
                .tenantId(snapshot.getTenantId())
                .workspaceId(snapshot.getWorkspaceId())
                .campaignId(snapshot.getCampaignId())
                .jobId(snapshot.getJobId())
                .batchId(snapshot.getBatchId())
                .messageId(snapshot.getMessageId())
                .contentId(snapshot.getContentId())
                .subjectSha256(snapshot.getSubjectSha256())
                .htmlSha256(snapshot.getHtmlSha256())
                .textSha256(snapshot.getTextSha256())
                .subjectBytes(snapshot.getSubjectBytes())
                .htmlBytes(snapshot.getHtmlBytes())
                .textBytes(snapshot.getTextBytes())
                .createdAt(snapshot.getCreatedAt())
                .expiresAt(snapshot.getExpiresAt())
                .inlineFallbackIncluded(snapshot.getInlineFallbackIncluded())
                .build();
    }

    private StoredRenderedContent toStored(RenderedContentSnapshot snapshot) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("tenantId", snapshot.getTenantId());
        metadata.put("workspaceId", snapshot.getWorkspaceId());
        metadata.put("campaignId", snapshot.getCampaignId());
        metadata.put("jobId", snapshot.getJobId());
        metadata.put("batchId", snapshot.getBatchId());
        metadata.put("messageId", snapshot.getMessageId());
        metadata.put("contentId", snapshot.getContentId());
        metadata.put("referenceId", snapshot.getReferenceId());
        return new StoredRenderedContent(
                snapshot.getTenantId(),
                snapshot.getWorkspaceId(),
                snapshot.getCampaignId(),
                snapshot.getJobId(),
                snapshot.getBatchId(),
                snapshot.getMessageId(),
                snapshot.getContentId(),
                snapshot.getReferenceId(),
                snapshot.getSubject(),
                snapshot.getHtmlBody(),
                snapshot.getTextBody(),
                snapshot.getExpiresAt(),
                metadata);
    }

    private void validateScope(String tenantId, String workspaceId, SnapshotRequest request) {
        if (request == null) {
            throw new ValidationException("contentReference", "Rendered content snapshot request is required");
        }
        if (!tenantId.equals(request.tenantId()) || !workspaceId.equals(request.workspaceId())) {
            throw new ValidationException("scope", "Rendered content snapshot scope does not match request context");
        }
    }

    private void validate(SnapshotRequest request) {
        require(request.campaignId(), "campaignId");
        require(request.jobId(), "jobId");
        require(request.batchId(), "batchId");
        require(request.messageId(), "messageId");
        require(request.contentId(), "contentId");
        require(request.subject(), "subject");
        require(request.htmlBody(), "htmlBody");
    }

    private String referenceId(String tenantId, String workspaceId, SnapshotRequest request) {
        String seed = String.join(":",
                tenantId,
                workspaceId,
                request.campaignId(),
                request.jobId(),
                request.batchId(),
                request.messageId(),
                request.contentId());
        return "cr_" + sha256Hex(seed).substring(0, 32);
    }

    private String require(String value, String field) {
        if (isBlank(value)) {
            throw new ValidationException(field, field + " is required for rendered content snapshot");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private Duration effectiveSnapshotTtl() {
        if (snapshotTtl == null || snapshotTtl.isZero() || snapshotTtl.isNegative()) {
            return Duration.ofHours(72);
        }
        return snapshotTtl;
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

    public record SnapshotRequest(String tenantId,
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

    public record StoredRenderedContent(String tenantId,
                                        String workspaceId,
                                        String campaignId,
                                        String jobId,
                                        String batchId,
                                        String messageId,
                                        String contentId,
                                        String referenceId,
                                        String subject,
                                        String htmlBody,
                                        String textBody,
                                        Instant expiresAt,
                                        Map<String, String> metadata) {
    }
}

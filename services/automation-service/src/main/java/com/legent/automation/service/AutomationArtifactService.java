package com.legent.automation.service;

import com.legent.automation.domain.AutomationArtifact;
import com.legent.automation.dto.AutomationStudioDto;
import com.legent.automation.repository.AutomationArtifactRepository;
import com.legent.common.exception.ValidationException;
import com.legent.common.util.IdGenerator;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AutomationArtifactService {

    private static final long MAX_ARTIFACT_SIZE_BYTES = 104_857_600L;
    private static final Pattern SHA_256 = Pattern.compile("^[a-fA-F0-9]{64}$");
    private static final Set<String> CSV_CONTENT_TYPES = Set.of(
            "text/csv",
            "application/csv",
            "application/vnd.ms-excel",
            "text/plain");

    private final AutomationArtifactRepository artifactRepository;

    @Transactional
    public AutomationStudioDto.ArtifactResponse createArtifact(AutomationStudioDto.ArtifactRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        AutomationArtifact artifact = new AutomationArtifact();
        artifact.setId(IdGenerator.newId());
        artifact.setTenantId(tenantId);
        artifact.setWorkspaceId(workspaceId);
        artifact.setActivityId(normalizeBlank(request.getActivityId()));
        artifact.setSourceKind(request.getSourceKind() == null
                ? AutomationArtifact.SourceKind.UPLOAD
                : request.getSourceKind());
        artifact.setStatus(request.getStatus() == null
                ? AutomationArtifact.ArtifactStatus.READY
                : request.getStatus());
        artifact.setDisplayName(requireSafeFileName(request.getDisplayName()));
        artifact.setContentType(requireAllowedCsvContentType(request.getContentType()));
        artifact.setSizeBytes(requireSize(request.getSizeBytes()));
        artifact.setSha256(requireSha256(request.getSha256()));
        artifact.setRetentionPolicy(normalizeRetentionPolicy(request.getRetentionPolicy()));
        artifact.setExpiresAt(request.getExpiresAt());
        artifact.setObjectKey(objectKey(tenantId, workspaceId, artifact));
        return toResponse(artifactRepository.save(artifact));
    }

    @Transactional(readOnly = true)
    public AutomationStudioDto.ArtifactResponse getArtifact(String artifactId) {
        return toResponse(requireCurrentWorkspaceArtifact(artifactId));
    }

    @Transactional(readOnly = true)
    public AutomationArtifact requireImportArtifact(String artifactId) {
        AutomationArtifact artifact = requireCurrentWorkspaceArtifact(artifactId);
        validateReadyArtifact(artifact);
        requireAllowedCsvContentType(artifact.getContentType());
        requireSafeGeneratedObjectKey(artifact);
        if (!artifact.getObjectKey().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ValidationException("artifactId", "Automation import artifacts must reference CSV objects");
        }
        return artifact;
    }

    @Transactional(readOnly = true)
    public AutomationArtifact requireExtractArtifact(String artifactId) {
        AutomationArtifact artifact = requireCurrentWorkspaceArtifact(artifactId);
        if (artifact.getStatus() != AutomationArtifact.ArtifactStatus.READY
                && artifact.getStatus() != AutomationArtifact.ArtifactStatus.GENERATED) {
            throw new ValidationException("artifactId", "Automation extract artifacts must be READY or GENERATED");
        }
        requireAllowedCsvContentType(artifact.getContentType());
        requireSafeGeneratedObjectKey(artifact);
        return artifact;
    }

    @Transactional(readOnly = true)
    public AutomationArtifact requireMovementSourceArtifact(String artifactId) {
        AutomationArtifact artifact = requireCurrentWorkspaceArtifact(artifactId);
        validateReadableMovementArtifact(artifact);
        return artifact;
    }

    @Transactional(readOnly = true)
    public AutomationArtifact requireMovementTargetArtifact(String artifactId) {
        AutomationArtifact artifact = requireCurrentWorkspaceArtifact(artifactId);
        validateReadableMovementArtifact(artifact);
        return artifact;
    }

    @Transactional
    public AutomationArtifact markGenerated(AutomationArtifact artifact) {
        AutomationArtifact scopedArtifact = requireCurrentWorkspaceArtifact(artifact.getId());
        if (!scopedArtifact.getTenantId().equals(artifact.getTenantId())
                || !scopedArtifact.getWorkspaceId().equals(artifact.getWorkspaceId())) {
            throw new ValidationException("artifactId", "Automation artifact must exist in the current workspace");
        }
        scopedArtifact.setStatus(AutomationArtifact.ArtifactStatus.GENERATED);
        return artifactRepository.save(scopedArtifact);
    }

    public Map<String, Object> summary(AutomationArtifact artifact) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("artifactId", artifact.getId());
        summary.put("sourceKind", artifact.getSourceKind().name());
        summary.put("status", artifact.getStatus().name());
        summary.put("contentType", artifact.getContentType());
        summary.put("sizeBytes", artifact.getSizeBytes());
        summary.put("sha256", artifact.getSha256());
        summary.put("retentionPolicy", artifact.getRetentionPolicy());
        summary.put("expiresAt", artifact.getExpiresAt() == null ? null : artifact.getExpiresAt().toString());
        return summary;
    }

    private AutomationArtifact requireCurrentWorkspaceArtifact(String artifactId) {
        String normalizedId = normalizeBlank(artifactId);
        if (normalizedId == null) {
            throw new ValidationException("artifactId", "Automation activity requires a scoped artifactId");
        }
        return artifactRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                        normalizedId,
                        TenantContext.requireTenantId(),
                        TenantContext.requireWorkspaceId())
                .orElseThrow(() -> new ValidationException("artifactId",
                        "Automation artifact must exist in the current workspace"));
    }

    private void validateReadyArtifact(AutomationArtifact artifact) {
        if (artifact.getStatus() != AutomationArtifact.ArtifactStatus.READY) {
            throw new ValidationException("artifactId", "Automation artifact must be READY");
        }
        if (artifact.getExpiresAt() != null && artifact.getExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException("artifactId", "Automation artifact has expired");
        }
        requireSize(artifact.getSizeBytes());
        requireSha256(artifact.getSha256());
    }

    private void validateReadableMovementArtifact(AutomationArtifact artifact) {
        if (artifact.getStatus() != AutomationArtifact.ArtifactStatus.READY
                && artifact.getStatus() != AutomationArtifact.ArtifactStatus.GENERATED) {
            throw new ValidationException("artifactId", "Automation movement artifacts must be READY or GENERATED");
        }
        if (artifact.getExpiresAt() != null && artifact.getExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException("artifactId", "Automation artifact has expired");
        }
        requireAllowedCsvContentType(artifact.getContentType());
        requireSafeGeneratedObjectKey(artifact);
        requireSize(artifact.getSizeBytes());
        requireSha256(artifact.getSha256());
    }

    private String objectKey(String tenantId, String workspaceId, AutomationArtifact artifact) {
        return "tenants/" + tenantId
                + "/workspaces/" + workspaceId
                + "/automation-artifacts/" + artifact.getId()
                + "/" + artifact.getDisplayName();
    }

    private void requireSafeGeneratedObjectKey(AutomationArtifact artifact) {
        String objectKey = normalizeBlank(artifact.getObjectKey());
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        String expectedPrefix = "tenants/" + tenantId + "/workspaces/" + workspaceId + "/automation-artifacts/" + artifact.getId() + "/";
        if (objectKey == null
                || objectKey.length() > 512
                || objectKey.contains("..")
                || objectKey.contains("\\")
                || objectKey.startsWith("/")
                || hasUriScheme(objectKey)
                || !objectKey.startsWith(expectedPrefix)) {
            throw new ValidationException("artifactId", "Automation artifact object key is not service generated for this workspace");
        }
    }

    private String requireSafeFileName(String value) {
        String fileName = normalizeBlank(value);
        if (fileName == null
                || fileName.length() > 255
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.contains("..")
                || fileName.startsWith(".")
                || hasUriScheme(fileName)
                || !fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ValidationException("displayName", "Automation artifact displayName must be a safe CSV file name");
        }
        return fileName;
    }

    private String requireAllowedCsvContentType(String value) {
        String contentType = normalizeContentType(value);
        if (contentType == null || !CSV_CONTENT_TYPES.contains(contentType)) {
            throw new ValidationException("contentType", "Automation artifact contentType must be CSV-compatible");
        }
        return contentType;
    }

    private Long requireSize(Long sizeBytes) {
        if (sizeBytes == null || sizeBytes < 1 || sizeBytes > MAX_ARTIFACT_SIZE_BYTES) {
            throw new ValidationException("sizeBytes", "Automation artifact sizeBytes must be between 1 and 104857600");
        }
        return sizeBytes;
    }

    private String requireSha256(String value) {
        String sha256 = normalizeBlank(value);
        if (sha256 == null || !SHA_256.matcher(sha256).matches()) {
            throw new ValidationException("sha256", "Automation artifact sha256 must be a 64-character hex digest");
        }
        return sha256.toLowerCase(Locale.ROOT);
    }

    private String normalizeRetentionPolicy(String value) {
        String retentionPolicy = normalizeBlank(value);
        return retentionPolicy == null ? "AUTOMATION_30_DAYS" : retentionPolicy;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasUriScheme(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return List.of("http://", "https://", "s3://", "gs://", "file:").stream().anyMatch(lower::startsWith);
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AutomationStudioDto.ArtifactResponse toResponse(AutomationArtifact artifact) {
        return AutomationStudioDto.ArtifactResponse.builder()
                .artifactId(artifact.getId())
                .activityId(artifact.getActivityId())
                .sourceKind(artifact.getSourceKind())
                .status(artifact.getStatus())
                .displayName(artifact.getDisplayName())
                .contentType(artifact.getContentType())
                .sizeBytes(artifact.getSizeBytes())
                .sha256(artifact.getSha256())
                .retentionPolicy(artifact.getRetentionPolicy())
                .expiresAt(artifact.getExpiresAt())
                .createdAt(artifact.getCreatedAt())
                .build();
    }
}

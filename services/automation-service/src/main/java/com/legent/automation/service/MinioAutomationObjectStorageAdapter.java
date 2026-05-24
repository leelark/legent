package com.legent.automation.service;

import com.legent.automation.domain.AutomationArtifact;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

@Service
public class MinioAutomationObjectStorageAdapter implements AutomationObjectStorageAdapter {

    private static final int BUFFER_SIZE = 8192;
    private static final long MAX_MOVEMENT_BYTES = 104_857_600L;
    private static final Set<String> CSV_CONTENT_TYPES = Set.of(
            "text/csv",
            "application/csv",
            "application/vnd.ms-excel",
            "text/plain");

    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;
    private final boolean enabled;
    private MinioClient minioClient;

    public MinioAutomationObjectStorageAdapter(@Value("${minio.endpoint}") String endpoint,
                                               @Value("${minio.access-key}") String accessKey,
                                               @Value("${minio.secret-key}") String secretKey,
                                               @Value("${legent.automation.storage.bucket:${minio.bucket}}") String bucket,
                                               @Value("${legent.automation.storage.enabled:true}") boolean enabled) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucket = bucket;
        this.enabled = enabled;
    }

    @PostConstruct
    void initialize() {
        if (!enabled) {
            return;
        }
        if (isBlank(endpoint) || isBlank(accessKey) || isBlank(secretKey) || isBlank(bucket)) {
            throw new AutomationObjectStorageException("Automation object storage adapter is missing required configuration");
        }
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception ex) {
            throw new AutomationObjectStorageException("Automation object storage adapter failed to initialize", ex);
        }
    }

    @Override
    public MovementResult copyArtifact(AutomationArtifact sourceArtifact,
                                       AutomationArtifact targetArtifact,
                                       MovementRequest request) {
        if (!enabled || minioClient == null) {
            throw new AutomationObjectStorageException("Automation object storage adapter is disabled");
        }
        validateScope(sourceArtifact, request);
        validateScope(targetArtifact, request);
        validateRegisteredMetadata(sourceArtifact);
        validateRegisteredMetadata(targetArtifact);
        requireTargetMatchesSource(sourceArtifact, targetArtifact);

        VerifiedObject sourceObject = verifyStoredObject(sourceArtifact);
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(bucket)
                    .object(targetArtifact.getObjectKey())
                    .source(CopySource.builder()
                            .bucket(bucket)
                            .object(sourceArtifact.getObjectKey())
                            .build())
                    .build());
        } catch (Exception ex) {
            throw new AutomationObjectStorageException("Automation file movement failed during object copy", ex);
        }
        VerifiedObject targetObject = verifyStoredObject(targetArtifact);
        if (targetObject.sizeBytes() != sourceObject.sizeBytes()
                || !targetObject.sha256().equals(sourceObject.sha256())) {
            throw new AutomationObjectStorageException("Automation file movement failed storage integrity verification");
        }
        return new MovementResult(
                request.operation(),
                sourceArtifact.getId(),
                targetArtifact.getId(),
                targetObject.sizeBytes(),
                targetObject.sha256(),
                targetObject.contentType(),
                Instant.now());
    }

    private void validateScope(AutomationArtifact artifact, MovementRequest request) {
        if (artifact == null
                || !request.tenantId().equals(artifact.getTenantId())
                || !request.workspaceId().equals(artifact.getWorkspaceId())) {
            throw new AutomationObjectStorageException("Automation artifact is outside the current tenant workspace");
        }
        String expectedPrefix = "tenants/" + request.tenantId()
                + "/workspaces/" + request.workspaceId()
                + "/automation-artifacts/" + artifact.getId() + "/";
        String objectKey = artifact.getObjectKey();
        if (objectKey == null
                || objectKey.length() > 512
                || objectKey.contains("..")
                || objectKey.contains("\\")
                || objectKey.startsWith("/")
                || hasUriScheme(objectKey)
                || !objectKey.startsWith(expectedPrefix)) {
            throw new AutomationObjectStorageException("Automation artifact object key is not service generated for this workspace");
        }
    }

    private void validateRegisteredMetadata(AutomationArtifact artifact) {
        if (artifact.getSizeBytes() == null || artifact.getSizeBytes() < 1 || artifact.getSizeBytes() > MAX_MOVEMENT_BYTES) {
            throw new AutomationObjectStorageException("Automation artifact size is outside movement limits");
        }
        if (artifact.getSha256() == null || !artifact.getSha256().matches("^[a-fA-F0-9]{64}$")) {
            throw new AutomationObjectStorageException("Automation artifact checksum metadata is invalid");
        }
        if (!CSV_CONTENT_TYPES.contains(normalizeContentType(artifact.getContentType()))) {
            throw new AutomationObjectStorageException("Automation artifact content type is not CSV-compatible");
        }
    }

    private void requireTargetMatchesSource(AutomationArtifact sourceArtifact, AutomationArtifact targetArtifact) {
        if (!sourceArtifact.getSizeBytes().equals(targetArtifact.getSizeBytes())
                || !sourceArtifact.getSha256().equalsIgnoreCase(targetArtifact.getSha256())
                || !normalizeContentType(sourceArtifact.getContentType()).equals(normalizeContentType(targetArtifact.getContentType()))) {
            throw new AutomationObjectStorageException("Automation file movement target metadata must match the source artifact");
        }
    }

    private VerifiedObject verifyStoredObject(AutomationArtifact artifact) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(artifact.getObjectKey())
                    .build());
            if (stat.size() != artifact.getSizeBytes()) {
                throw new AutomationObjectStorageException("Automation stored object size does not match registered metadata");
            }
            String statContentType = normalizeContentType(stat.contentType());
            if (statContentType != null && !statContentType.equals(normalizeContentType(artifact.getContentType()))) {
                throw new AutomationObjectStorageException("Automation stored object content type does not match registered metadata");
            }
            String sha256 = sha256(artifact);
            if (!sha256.equalsIgnoreCase(artifact.getSha256())) {
                throw new AutomationObjectStorageException("Automation stored object checksum does not match registered metadata");
            }
            return new VerifiedObject(stat.size(), sha256, normalizeContentType(artifact.getContentType()));
        } catch (AutomationObjectStorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AutomationObjectStorageException("Automation stored object could not be verified", ex);
        }
    }

    private String sha256(AutomationArtifact artifact) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0L;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(artifact.getObjectKey())
                .build())) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_MOVEMENT_BYTES) {
                    throw new AutomationObjectStorageException("Automation stored object exceeds movement limits");
                }
                digest.update(buffer, 0, read);
            }
        }
        if (total != artifact.getSizeBytes()) {
            throw new AutomationObjectStorageException("Automation stored object size does not match registered metadata");
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String normalizeContentType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasUriScheme(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("s3://")
                || lower.startsWith("gs://")
                || lower.startsWith("file:");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record VerifiedObject(long sizeBytes, String sha256, String contentType) {
    }
}

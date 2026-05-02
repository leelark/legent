package com.legent.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.content.domain.Asset;
import com.legent.content.dto.AssetDto;
import com.legent.content.repository.AssetRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import org.springframework.lang.NonNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final ObjectMapper objectMapper;

    private MinioClient minioClient;

    @Value("${minio.bucket:legent-assets}")
    private String defaultBucket;

    @Value("${minio.endpoint}")
    private String minioEndpoint;
    @Value("${minio.access-key}")
    private String minioAccessKey;
    @Value("${minio.secret-key}")
    private String minioSecretKey;

    @PostConstruct
    public void initMinio() {
        this.minioClient = MinioClient.builder()
                .endpoint(minioEndpoint)
                .credentials(minioAccessKey, minioSecretKey)
                .build();
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(defaultBucket).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(defaultBucket).build());
                log.info("Created MinIO bucket: {}", defaultBucket);
            }
            log.info("MinIO initialized successfully at {}", minioEndpoint);
        } catch (Exception e) {
            // AUDIT-018: Fail fast on required storage dependency
            throw new IllegalStateException("Failed to initialize MinIO storage at " + minioEndpoint + 
                    ". Asset operations will not be available.", e);
        }
    }

    /**
     * Uploads a file to MinIO and returns the storage URL.
     */
    public String uploadToMinio(MultipartFile file, String objectName) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(defaultBucket)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            return minioEndpoint + "/" + defaultBucket + "/" + objectName;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload to MinIO", e);
        }
    }

    /**
     * Deletes a file from MinIO.
     */
    public void deleteFromMinio(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(defaultBucket)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete from MinIO", e);
        }
    }

    @Transactional
    public Asset createAsset(AssetDto.Create request) {
        String tenantId = TenantContext.requireTenantId();

        String fileName = extractFileName(request.getUrl(), request.getName());
        if (assetRepository.existsByTenantIdAndFileNameAndDeletedAtIsNull(tenantId, fileName)) {
            throw new ConflictException("Asset with name '" + fileName + "' already exists");
        }

        Asset asset = new Asset();
        asset.setTenantId(tenantId);
        asset.setName(request.getName());
        asset.setFileName(fileName);
        asset.setContentType(request.getContentType());
        asset.setSizeBytes(request.getSize());
        asset.setStoragePath(request.getUrl());
        asset.setStorageBucket(defaultBucket);
        asset.setAltText(request.getName());
        asset.setTags(null);
        asset.setMetadata(serializeMetadata(request.getMetadata()));

        return assetRepository.save(asset);
    }

    public Asset getAsset(@NonNull String tenantId, @NonNull String id) {
        return assetRepository.findById(id)
                .filter(asset -> tenantId.equals(asset.getTenantId()) && asset.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Asset not found"));
    }

    public Page<Asset> listAssets(String tenantId, Pageable pageable) {
        return assetRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable);
    }

    @Transactional
    public void deleteAsset(@NonNull String tenantId, @NonNull String id) {
        Asset asset = getAsset(tenantId, id);
        String objectName = resolveObjectName(asset);
        
        // Delete from storage first (external operation) - if this fails, DB won't be modified
        deleteFromMinio(objectName);
        
        // Only soft-delete in DB after successful storage deletion
        asset.setDeletedAt(Instant.now());
        assetRepository.save(asset);
    }

    private String serializeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize asset metadata", e);
        }
    }

    public String extractFileName(String url, String defaultName) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path != null && !path.isBlank()) {
                int index = path.lastIndexOf('/');
                if (index >= 0 && index < path.length() - 1) {
                    return path.substring(index + 1);
                }
            }
        } catch (IllegalArgumentException ignored) {
        }
        return defaultName;
    }

    private String resolveObjectName(Asset asset) {
        String storagePath = asset.getStoragePath();
        if (storagePath == null || storagePath.isBlank()) {
            return asset.getFileName();
        }

        try {
            URI uri = URI.create(storagePath);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return asset.getFileName();
            }

            String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
            String bucketPrefix = defaultBucket + "/";
            if (normalizedPath.startsWith(bucketPrefix) && normalizedPath.length() > bucketPrefix.length()) {
                return normalizedPath.substring(bucketPrefix.length());
            }

            int separatorIndex = normalizedPath.indexOf('/');
            if (separatorIndex >= 0 && separatorIndex < normalizedPath.length() - 1) {
                return normalizedPath.substring(separatorIndex + 1);
            }

            return asset.getFileName();
        } catch (IllegalArgumentException ignored) {
            return asset.getFileName();
        }
    }
}

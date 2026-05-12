package com.legent.audience.service;

import com.legent.audience.domain.ImportJob;
import com.legent.audience.dto.ImportDto;
import com.legent.audience.event.ImportEventPublisher;
import com.legent.audience.repository.ImportJobRepository;
import com.legent.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final ImportJobRepository importJobRepository;
    private final ImportProcessingService processingService;
    private final ImportEventPublisher eventPublisher;
    private final io.minio.MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${legent.audience.import.max-file-size-bytes:104857600}")
    private long maxImportFileSizeBytes;

    @Value("${legent.audience.import.allowed-content-types:text/csv,application/csv,application/vnd.ms-excel,text/plain}")
    private String allowedImportContentTypes;

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            boolean found = minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucket).build());
            if (!found) {
                minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            log.error("Failed to initialize MinIO bucket", e);
        }
    }

    @Transactional
    public ImportDto.StatusResponse uploadAndStartImport(MultipartFile file, ImportDto.StartRequest request) {
        validateImportFile(file);
        try {
            String objectName = "import_" + java.util.UUID.randomUUID() + ".csv";
            minioClient.putObject(
                io.minio.PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build()
            );
            
            request.setFileName(objectName);
            request.setFileSize(file.getSize());
            return startImport(request);
        } catch (Exception e) {
            log.error("Failed to upload import file to MinIO", e);
            throw new RuntimeException("Failed to upload import file to MinIO", e);
        }
    }

    @Transactional
    public ImportDto.StatusResponse startImport(ImportDto.StartRequest request) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();

        if (request.getFieldMapping() == null || request.getFieldMapping().get("email") == null || request.getFieldMapping().get("email").isBlank()) {
            throw new IllegalArgumentException("Email mapping is required for subscriber imports");
        }

        ImportJob job = new ImportJob();
        job.setTenantId(tenantId);
        job.setWorkspaceId(workspaceId);
        job.setFileName(request.getFileName());
        job.setFileSize(request.getFileSize());
        job.setTargetType(request.getTargetType() != null ? request.getTargetType() : "SUBSCRIBER");
        job.setTargetId(request.getTargetId());
        job.setFieldMapping(request.getFieldMapping());
        job.setErrors(Collections.emptyList());

        ImportJob saved = importJobRepository.save(job);
        log.info("Import job created: id={}, file={}", saved.getId(), saved.getFileName());

        eventPublisher.publishStarted(saved);

        // Kick off async processing
        processingService.processImport(saved.getId());

        return toStatusResponse(saved);
    }

    @Transactional(readOnly = true)
    public ImportDto.StatusResponse getStatus(String jobId) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        ImportJob job = importJobRepository.findByTenantIdAndWorkspaceIdAndId(tenantId, workspaceId, jobId)
                .orElseThrow(() -> new NotFoundException("ImportJob", jobId));
        return toStatusResponse(job);
    }

    @Transactional(readOnly = true)
    public Page<ImportDto.StatusResponse> listImports(Pageable pageable) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        return importJobRepository.findByTenantAndWorkspace(tenantId, workspaceId, pageable).map(this::toStatusResponse);
    }

    @Transactional
    public void cancelImport(String jobId) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        ImportJob job = importJobRepository.findByTenantIdAndWorkspaceIdAndId(tenantId, workspaceId, jobId)
                .orElseThrow(() -> new NotFoundException("ImportJob", jobId));

        if (job.getStatus() == ImportJob.ImportStatus.PENDING || job.getStatus() == ImportJob.ImportStatus.PROCESSING) {
            job.setStatus(ImportJob.ImportStatus.CANCELLED);
            importJobRepository.save(job);
            log.info("Import cancelled: id={}", jobId);
        }
    }

    private ImportDto.StatusResponse toStatusResponse(ImportJob job) {
        double progress = job.getTotalRows() > 0
                ? (double) job.getProcessedRows() / job.getTotalRows() * 100
                : 0;

        return ImportDto.StatusResponse.builder()
                .id(job.getId()).fileName(job.getFileName())
                .status(job.getStatus().name())
                .targetType(job.getTargetType())
                .totalRows(job.getTotalRows()).processedRows(job.getProcessedRows())
                .successRows(job.getSuccessRows()).errorRows(job.getErrorRows())
                .progressPercent(Math.round(progress * 100.0) / 100.0)
                .startedAt(job.getStartedAt()).completedAt(job.getCompletedAt())
                .createdAt(job.getCreatedAt()).build();
    }

    void validateImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Import file is required");
        }
        if (file.getSize() > maxImportFileSizeBytes) {
            throw new IllegalArgumentException("Import file exceeds max size of " + maxImportFileSizeBytes + " bytes");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (contentType == null || !allowedContentTypes().contains(contentType)) {
            throw new IllegalArgumentException("Import file content type is not allowed: " + contentType);
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.trim().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("Import file must be a CSV");
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private Set<String> allowedContentTypes() {
        return Arrays.stream(allowedImportContentTypes.split(","))
                .map(this::normalizeContentType)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}

package com.legent.audience.service;

import com.legent.audience.domain.ImportJob;
import com.legent.audience.dto.ImportDto;
import com.legent.audience.event.ImportEventPublisher;
import com.legent.audience.repository.ImportJobRepository;
import com.legent.common.exception.NotFoundException;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final ImportJobRepository importJobRepository;
    private final ImportProcessingService processingService;
    private final ImportEventPublisher eventPublisher;

    @Transactional
    public ImportDto.StatusResponse uploadAndStartImport(org.springframework.web.multipart.MultipartFile file, ImportDto.StartRequest request) {
        try {
            java.io.File tempFile = java.io.File.createTempFile("import_", ".csv");
            file.transferTo(tempFile);
            request.setFileName(tempFile.getAbsolutePath());
            request.setFileSize(file.getSize());
            return startImport(request);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to save imported file", e);
        }
    }

    @Transactional
    public ImportDto.StatusResponse startImport(ImportDto.StartRequest request) {
        String tenantId = TenantContext.getTenantId();

        ImportJob job = new ImportJob();
        job.setTenantId(tenantId);
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
        String tenantId = TenantContext.getTenantId();
        ImportJob job = importJobRepository.findByTenantIdAndId(tenantId, jobId)
                .orElseThrow(() -> new NotFoundException("ImportJob", jobId));
        return toStatusResponse(job);
    }

    @Transactional(readOnly = true)
    public Page<ImportDto.StatusResponse> listImports(Pageable pageable) {
        String tenantId = TenantContext.getTenantId();
        return importJobRepository.findByTenant(tenantId, pageable).map(this::toStatusResponse);
    }

    @Transactional
    public void cancelImport(String jobId) {
        String tenantId = TenantContext.getTenantId();
        ImportJob job = importJobRepository.findByTenantIdAndId(tenantId, jobId)
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
}

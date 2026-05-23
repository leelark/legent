package com.legent.audience.service;

import com.legent.common.constant.AppConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import java.util.Map;
import java.time.Instant;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import com.legent.audience.domain.ImportJob;
import com.legent.audience.domain.Subscriber;
import com.legent.audience.domain.SubscriberIdentityProvenance;
import com.legent.audience.dto.DataExtensionDto;
import com.legent.audience.event.ImportEventPublisher;
import com.legent.audience.repository.ImportJobRepository;
import com.legent.audience.repository.SubscriberIdentityProvenanceRepository;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.HashMap;
import java.util.LinkedHashMap;

import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Async chunk-based import processor.
 * Processes import data in chunks of 5000 rows with row-level validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class ImportProcessingService {

    private static final String IMPORT_SOURCE = "IMPORT";
    private static final String IMPORT_LEAD_SOURCE = "AUDIENCE_IMPORT";
    private static final String IMPORT_ACQUISITION_CHANNEL = "CSV_IMPORT";

    private final ImportJobRepository importJobRepository;
    private final SubscriberRepository subscriberRepository;
    private final SubscriberIdentityProvenanceRepository subscriberIdentityProvenanceRepository;
    private final DataExtensionService dataExtensionService;
    private final ImportEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final io.minio.MinioClient minioClient;

    @org.springframework.beans.factory.annotation.Value("${minio.bucket}")
    private String bucket;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @Async("importExecutor")
    public void processImport(String jobId, String tenantId, String workspaceId) {
        ImportJob job = findScopedJob(jobId, tenantId, workspaceId).orElse(null);
        if (job == null) {
            return;
        }

        TenantContext.setTenantId(tenantId);
        TenantContext.setWorkspaceId(workspaceId);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        try {
            ImportJob startedJob = markProcessing(jobId, tenantId, workspaceId, transactionTemplate);
            if (startedJob == null) {
                log.info("Import job disappeared before processing: id={}, tenant={}, workspace={}",
                        jobId, tenantId, workspaceId);
                return;
            }
            job = startedJob;
            if (!isBlank(job.getStartedBy())) {
                TenantContext.setUserId(job.getStartedBy());
            }
            populateTargetProvenance(job);

            long successCount = 0;
            long errorCount = 0;
            List<Map<String, Object>> errors = new ArrayList<>(AppConstants.IMPORT_MAX_ERRORS);
            int chunkSize = AppConstants.IMPORT_CHUNK_SIZE;
            int currentRow = 0;
            List<Map<String, String>> currentChunk = new ArrayList<>();

            try (java.io.InputStream is = minioClient.getObject(
                    io.minio.GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(job.getFileName())
                            .build());
                 Reader reader = new java.io.InputStreamReader(is, StandardCharsets.UTF_8)) {
                Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreEmptyLines(true)
                        .build()
                        .parse(reader);

                for (CSVRecord record : records) {
                    currentRow++;
                    currentChunk.add(record.toMap());

                    if (currentChunk.size() >= chunkSize) {
                        if (isCancelledOrMissing(jobId, tenantId, workspaceId)) {
                            log.info("Import cancelled or missing: id={}", jobId);
                            return;
                        }
                        ChunkResult result = processChunk(tenantId, workspaceId, currentChunk, job,
                                remainingErrorSampleCapacity(errors));
                        successCount += result.successCount;
                        errorCount += result.errorCount;
                        appendErrorSamples(errors, result.errors);

                        if (!updateJobProgress(jobId, tenantId, workspaceId, currentRow, successCount, errorCount)) {
                            log.info("Import job disappeared during progress update: id={}, tenant={}, workspace={}",
                                    jobId, tenantId, workspaceId);
                            return;
                        }
                        currentChunk.clear();

                        if (isCancelledOrMissing(jobId, tenantId, workspaceId)) {
                            log.info("Import cancelled: id={}", jobId);
                            return;
                        }
                    }
                }

                if (!currentChunk.isEmpty()) {
                    if (isCancelledOrMissing(jobId, tenantId, workspaceId)) {
                        log.info("Import cancelled or missing: id={}", jobId);
                        return;
                    }
                    ChunkResult result = processChunk(tenantId, workspaceId, currentChunk, job,
                            remainingErrorSampleCapacity(errors));
                    successCount += result.successCount;
                    errorCount += result.errorCount;
                    appendErrorSamples(errors, result.errors);
                }
            }

            final long finalSuccess = successCount;
            final long finalError = errorCount;
            final List<Map<String, Object>> finalErrors = List.copyOf(errors);
            final int finalTotal = currentRow;

            completeJob(jobId, tenantId, workspaceId, finalSuccess, finalError, finalErrors, finalTotal,
                    transactionTemplate);

            log.info("Import completed: id={}, success={}, errors={}", jobId, successCount, errorCount);

        } catch (Exception e) {
            log.error("Import failed: id={}", jobId, e);
            failJob(jobId, tenantId, workspaceId, e, transactionTemplate);
        } finally {
            TenantContext.clear();
        }
    }

    private Optional<ImportJob> findScopedJob(String jobId, String tenantId, String workspaceId) {
        if (isBlank(jobId) || isBlank(tenantId) || isBlank(workspaceId)) {
            return Optional.empty();
        }
        return importJobRepository.findByTenantIdAndWorkspaceIdAndId(tenantId, workspaceId, jobId);
    }

    private ImportJob markProcessing(String jobId, String tenantId, String workspaceId,
                                     TransactionTemplate transactionTemplate) {
        return transactionTemplate.execute(status -> findScopedJob(jobId, tenantId, workspaceId)
                .map(j -> {
                    j.setStatus(ImportJob.ImportStatus.PROCESSING);
                    j.setStartedAt(Instant.now());
                    return importJobRepository.save(j);
                })
                .orElse(null));
    }

    private boolean updateJobProgress(String jobId, String tenantId, String workspaceId,
                                      int processed, long success, long error) {
        Boolean updated = new TransactionTemplate(transactionManager).execute(status -> findScopedJob(jobId, tenantId, workspaceId)
                .map(j -> {
                    j.setProcessedRows(processed);
                    j.setSuccessRows(success);
                    j.setErrorRows(error);
                    importJobRepository.save(j);
                    return true;
                })
                .orElse(false));
        return Boolean.TRUE.equals(updated);
    }

    private void completeJob(String jobId, String tenantId, String workspaceId,
                             long finalSuccess, long finalError, List<Map<String, Object>> finalErrors,
                             int finalTotal, TransactionTemplate transactionTemplate) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                findScopedJob(jobId, tenantId, workspaceId).ifPresent(j -> {
                    j.setTotalRows(finalTotal);
                    j.setProcessedRows(finalTotal);
                    j.setSuccessRows(finalSuccess);
                    j.setErrorRows(finalError);
                    j.setStatus(finalError > 0 ? ImportJob.ImportStatus.COMPLETED_WITH_ERRORS : ImportJob.ImportStatus.COMPLETED);
                    j.setCompletedAt(Instant.now());
                    j.setErrors(finalErrors);
                    importJobRepository.save(j);
                    eventPublisher.publishCompleted(j);
                });
            }
        });
    }

    private void failJob(String jobId, String tenantId, String workspaceId, Exception e,
                         TransactionTemplate transactionTemplate) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                findScopedJob(jobId, tenantId, workspaceId).ifPresent(j -> {
                    j.setStatus(ImportJob.ImportStatus.FAILED);
                    j.setCompletedAt(Instant.now());
                    importJobRepository.save(j);
                    eventPublisher.publishFailed(j, e.getMessage());
                });
            }
        });
    }

    private boolean isCancelledOrMissing(String jobId, String tenantId, String workspaceId) {
        return findScopedJob(jobId, tenantId, workspaceId)
                .map(j -> j.getStatus() == ImportJob.ImportStatus.CANCELLED)
                .orElse(true);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static class ChunkResult {
        long successCount = 0;
        long errorCount = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        private final int maxErrorSamples;

        ChunkResult(int maxErrorSamples) {
            this.maxErrorSamples = Math.max(0, maxErrorSamples);
        }

        void addError(Exception e) {
            errorCount++;
            if (errors.size() < maxErrorSamples) {
                errors.add(Map.of("message", e.getMessage()));
            }
        }
    }

    private ChunkResult processChunk(String tenantId, String workspaceId, List<Map<String, String>> chunk, ImportJob job,
                                     int remainingErrorSamples) {
        String targetType = job.getTargetType() == null ? "SUBSCRIBER" : job.getTargetType().trim().toUpperCase(Locale.ROOT);
        if ("DATA_EXTENSION".equals(targetType)) {
            return processDataExtensionChunk(chunk, job.getTargetId(), job.getFieldMapping(), remainingErrorSamples);
        }
        return processSubscriberChunk(tenantId, workspaceId, chunk, job, remainingErrorSamples);
    }

    private ChunkResult processSubscriberChunk(String tenantId, String workspaceId, List<Map<String, String>> chunk,
                                               ImportJob job, int remainingErrorSamples) {
        ChunkResult result = new ChunkResult(remainingErrorSamples);
        List<Subscriber> toSave = new ArrayList<>();
        Map<String, String> mapping = job.getFieldMapping();

        for (Map<String, String> row : chunk) {
            try {
                String email = getMappedField(row, mapping, "email");
                String subKeyRaw = getMappedField(row, mapping, "subscriberKey");
                String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
                final String subKey = (subKeyRaw == null || subKeyRaw.isBlank())
                        ? generateDeterministicSubscriberKey(normalizedEmail, workspaceId)
                        : subKeyRaw.trim();

                if (normalizedEmail == null || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
                    throw new IllegalArgumentException("Invalid email: " + email);
                }

                Optional<Subscriber> existing = subscriberRepository
                        .findByTenantIdAndWorkspaceIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, workspaceId, normalizedEmail);

                Subscriber sub = existing.orElseGet(() -> {
                    Subscriber s = new Subscriber();
                    s.setTenantId(tenantId);
                    s.setWorkspaceId(workspaceId);
                    s.setSubscriberKey(subKey);
                    s.setEmail(normalizedEmail);
                    s.setSubscribedAt(Instant.now());
                    s.setLifecycleStageAt(Instant.now());
                    return s;
                });

                applyMappedFields(sub, row, mapping);
                applyImportProvenance(sub, job);
                toSave.add(sub);
                result.successCount++;
            } catch (Exception e) {
                result.addError(e);
            }
        }

        if (!toSave.isEmpty()) {
            new TransactionTemplate(transactionManager).execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    List<Subscriber> savedSubscribers = subscriberRepository.saveAll(toSave);
                    List<SubscriberIdentityProvenance> provenanceRows = savedSubscribers.stream()
                            .filter(subscriber -> !isBlank(subscriber.getId()))
                            .map(subscriber -> subscriberProvenance(subscriber, job))
                            .toList();
                    if (!provenanceRows.isEmpty()) {
                        subscriberIdentityProvenanceRepository.saveAll(provenanceRows);
                    }
                }
            });
        }
        return result;
    }

    private ChunkResult processDataExtensionChunk(List<Map<String, String>> chunk, String targetId,
                                                  Map<String, String> mapping, int remainingErrorSamples) {
        ChunkResult result = new ChunkResult(remainingErrorSamples);
        for (Map<String, String> row : chunk) {
            try {
                Map<String, Object> mapped = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : mapping.entrySet()) {
                    String targetField = entry.getKey();
                    String sourceHeader = entry.getValue();
                    if (targetField != null && sourceHeader != null && row.containsKey(sourceHeader)) {
                        mapped.put(targetField, row.get(sourceHeader));
                    }
                }
                dataExtensionService.addRecord(targetId, DataExtensionDto.RecordRequest.builder()
                        .data(mapped)
                        .build());
                result.successCount++;
            } catch (Exception e) {
                result.addError(e);
            }
        }
        return result;
    }

    private void populateTargetProvenance(ImportJob job) {
        String targetType = job.getTargetType() == null ? "SUBSCRIBER" : job.getTargetType().trim().toUpperCase(Locale.ROOT);
        if ("DATA_EXTENSION".equals(targetType) && !isBlank(job.getTargetId())) {
            dataExtensionService.markImportProvenance(job.getTargetId(), job.getId(), job.getFileName());
        }
    }

    private void applyImportProvenance(Subscriber sub, ImportJob job) {
        sub.setSource(IMPORT_SOURCE);
        sub.setLeadSource(IMPORT_LEAD_SOURCE);
        sub.setAcquisitionChannel(IMPORT_ACQUISITION_CHANNEL);
        sub.setCampaignSource(importReference(job));
    }

    private String importReference(ImportJob job) {
        String id = job == null ? null : job.getId();
        if (id == null || id.isBlank()) {
            return IMPORT_LEAD_SOURCE;
        }
        String reference = "import:" + id.trim();
        return reference.length() <= 128 ? reference : reference.substring(0, 128);
    }

    private SubscriberIdentityProvenance subscriberProvenance(Subscriber subscriber, ImportJob job) {
        SubscriberIdentityProvenance provenance = new SubscriberIdentityProvenance();
        provenance.setTenantId(subscriber.getTenantId());
        provenance.setWorkspaceId(subscriber.getWorkspaceId());
        provenance.setSubscriberId(subscriber.getId());
        provenance.setSourceType(IMPORT_SOURCE);
        provenance.setSourceRef(job.getId());
        provenance.setCapturedAt(Instant.now());
        provenance.setCreatedBy(job.getStartedBy());
        provenance.setMetadata(Map.of(
                "importJobId", nullSafe(job.getId()),
                "fileName", nullSafe(job.getFileName()),
                "targetType", nullSafe(job.getTargetType() == null ? "SUBSCRIBER" : job.getTargetType())));
        return provenance;
    }

    private int remainingErrorSampleCapacity(List<Map<String, Object>> errors) {
        return Math.max(0, AppConstants.IMPORT_MAX_ERRORS - errors.size());
    }

    private void appendErrorSamples(List<Map<String, Object>> errors, List<Map<String, Object>> samples) {
        int remaining = remainingErrorSampleCapacity(errors);
        if (remaining <= 0 || samples.isEmpty()) {
            return;
        }
        errors.addAll(samples.size() <= remaining ? samples : samples.subList(0, remaining));
    }

    private void applyMappedFields(Subscriber sub, Map<String, String> row, Map<String, String> fieldMapping) {
        String firstName = getMappedField(row, fieldMapping, "firstName");
        String lastName = getMappedField(row, fieldMapping, "lastName");
        String phone = getMappedField(row, fieldMapping, "phone");
        String locale = getMappedField(row, fieldMapping, "locale");
        String timezone = getMappedField(row, fieldMapping, "timezone");

        if (firstName != null) sub.setFirstName(firstName);
        if (lastName != null) sub.setLastName(lastName);
        if (phone != null) sub.setPhone(phone);
        if (locale != null) sub.setLocale(locale);
        if (timezone != null) sub.setTimezone(timezone);

        // Map other fields to customFields
        Map<String, Object> customFields = sub.getCustomFields();
        if (customFields == null) customFields = new HashMap<>();

        for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
            String target = entry.getKey();
            if (isStandardField(target)) continue;

            String value = getMappedField(row, fieldMapping, target);
            if (value != null) {
                customFields.put(target, value);
            }
        }
        sub.setCustomFields(customFields);
    }

    private boolean isStandardField(String field) {
        return List.of("email", "subscriberKey", "firstName", "lastName", "phone", "locale", "timezone",
                "source", "leadSource", "acquisitionChannel", "campaignSource").contains(field);
    }

    private String getMappedField(Map<String, String> row, Map<String, String> mapping, String targetField) {
        if (mapping == null) return null;
        String sourceField = mapping.get(targetField);
        if (sourceField == null) return null;
        return row.get(sourceField);
    }

    private String generateDeterministicSubscriberKey(String normalizedEmail, String workspaceId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = normalizedEmail + "|" + workspaceId;
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return "sub-" + hex.substring(0, 24);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate subscriber key", e);
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}

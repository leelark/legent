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
import com.legent.audience.event.ImportEventPublisher;
import com.legent.audience.repository.ImportJobRepository;
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

    private final ImportJobRepository importJobRepository;
    private final SubscriberRepository subscriberRepository;
    private final ImportEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final io.minio.MinioClient minioClient;

    @org.springframework.beans.factory.annotation.Value("${minio.bucket}")
    private String bucket;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @Async("importExecutor")
    public void processImport(String jobId) {
        ImportJob job = importJobRepository.findById(jobId).orElse(null);
        if (job == null) return;

        String tenantId = job.getTenantId();
        String workspaceId = job.getWorkspaceId();
        TenantContext.setTenantId(tenantId);
        TenantContext.setWorkspaceId(workspaceId);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        try {
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    ImportJob j = importJobRepository.findById(jobId).get();
                    j.setStatus(ImportJob.ImportStatus.PROCESSING);
                    j.setStartedAt(Instant.now());
                    importJobRepository.save(j);
                }
            });

            long successCount = 0;
            long errorCount = 0;
            List<Map<String, Object>> errors = new ArrayList<>();
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
                        ChunkResult result = processChunk(tenantId, workspaceId, currentChunk, job.getFieldMapping());
                        successCount += result.successCount;
                        errorCount += result.errorCount;
                        errors.addAll(result.errors);
                        
                        updateJobProgress(jobId, currentRow, successCount, errorCount);
                        currentChunk.clear();

                        if (isCancelled(jobId)) {
                            log.info("Import cancelled: id={}", jobId);
                            return;
                        }
                    }
                }

                if (!currentChunk.isEmpty()) {
                    ChunkResult result = processChunk(tenantId, workspaceId, currentChunk, job.getFieldMapping());
                    successCount += result.successCount;
                    errorCount += result.errorCount;
                    errors.addAll(result.errors);
                }
            }

            final long finalSuccess = successCount;
            final long finalError = errorCount;
            final List<Map<String, Object>> finalErrors = errors.stream().limit(AppConstants.IMPORT_MAX_ERRORS).toList();
            final int finalTotal = currentRow;

            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    ImportJob j = importJobRepository.findById(jobId).get();
                    j.setTotalRows(finalTotal);
                    j.setProcessedRows(finalTotal);
                    j.setSuccessRows(finalSuccess);
                    j.setErrorRows(finalError);
                    j.setStatus(finalError > 0 ? ImportJob.ImportStatus.COMPLETED_WITH_ERRORS : ImportJob.ImportStatus.COMPLETED);
                    j.setCompletedAt(Instant.now());
                    j.setErrors(finalErrors);
                    importJobRepository.save(j);
                    eventPublisher.publishCompleted(j);
                }
            });

            log.info("Import completed: id={}, success={}, errors={}", jobId, successCount, errorCount);

        } catch (Exception e) {
            log.error("Import failed: id={}", jobId, e);
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    ImportJob j = importJobRepository.findById(jobId).get();
                    j.setStatus(ImportJob.ImportStatus.FAILED);
                    j.setCompletedAt(Instant.now());
                    importJobRepository.save(j);
                    eventPublisher.publishFailed(j, e.getMessage());
                }
            });
        } finally {
            TenantContext.clear();
        }
    }

    private void updateJobProgress(String jobId, int processed, long success, long error) {
        new TransactionTemplate(transactionManager).execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                importJobRepository.findById(jobId).ifPresent(j -> {
                    j.setProcessedRows(processed);
                    j.setSuccessRows(success);
                    j.setErrorRows(error);
                    importJobRepository.save(j);
                });
            }
        });
    }

    private boolean isCancelled(String jobId) {
        return importJobRepository.findById(jobId)
                .map(j -> j.getStatus() == ImportJob.ImportStatus.CANCELLED)
                .orElse(true);
    }

    private static class ChunkResult {
        long successCount = 0;
        long errorCount = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
    }

    private ChunkResult processChunk(String tenantId, String workspaceId, List<Map<String, String>> chunk, Map<String, String> mapping) {
        ChunkResult result = new ChunkResult();
        List<Subscriber> toSave = new ArrayList<>();

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
                toSave.add(sub);
                result.successCount++;
            } catch (Exception e) {
                result.errorCount++;
                result.errors.add(Map.of("message", e.getMessage()));
            }
        }

        if (!toSave.isEmpty()) {
            new TransactionTemplate(transactionManager).execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    subscriberRepository.saveAll(toSave);
                }
            });
        }
        return result;
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
        return List.of("email", "subscriberKey", "firstName", "lastName", "phone", "locale", "timezone").contains(field);
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
}

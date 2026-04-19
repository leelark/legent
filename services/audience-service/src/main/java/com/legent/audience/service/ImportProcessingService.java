package com.legent.audience.service;

import com.legent.common.constant.AppConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import java.util.Map;
import java.time.Instant;
import java.io.Reader;
import java.io.FileReader;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Async chunk-based import processor.
 * Processes import data in chunks of 5000 rows with row-level validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ImportProcessingService {

    private final ImportJobRepository importJobRepository;
    private final SubscriberRepository subscriberRepository;
    private final ImportEventPublisher eventPublisher;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @Async("importExecutor")
    @Transactional
    public void processImport(String jobId) {
        ImportJob job = importJobRepository.findById(jobId).orElse(null);
        if (job == null) return;

        String tenantId = job.getTenantId();
        TenantContext.setTenantId(tenantId);

        try {
            job.setStatus(ImportJob.ImportStatus.PROCESSING);
            job.setStartedAt(Instant.now());
            importJobRepository.save(job);

            long successCount = 0;
            long errorCount = 0;
            List<Map<String, Object>> errors = new ArrayList<>();
            int chunkSize = AppConstants.IMPORT_CHUNK_SIZE;
            int currentRow = 0;

            try (Reader reader = new FileReader(job.getFileName(), StandardCharsets.UTF_8)) {
                Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreEmptyLines(true)
                        .build()
                        .parse(reader);

                for (CSVRecord record : records) {
                    currentRow++;
                    
                    if (currentRow % chunkSize == 0) {
                        ImportJob current = importJobRepository.findById(jobId).orElse(null);
                        if (current == null || current.getStatus() == ImportJob.ImportStatus.CANCELLED) {
                            log.info("Import cancelled: id={}", jobId);
                            return;
                        }
                        job.setProcessedRows(currentRow);
                        job.setSuccessRows(successCount);
                        job.setErrorRows(errorCount);
                        importJobRepository.save(job);
                    }

                    try {
                        Map<String, String> rowMap = record.toMap();
                        processRow(tenantId, rowMap, job.getFieldMapping());
                        successCount++;
                    } catch (Exception e) {
                        errorCount++;
                        if (errors.size() < AppConstants.IMPORT_MAX_ERRORS) {
                            errors.add(Map.of(
                                    "rowNumber", currentRow,
                                    "message", e.getMessage()
                            ));
                        }
                    }
                }
            }

            job.setTotalRows(currentRow);
            job.setProcessedRows(currentRow);
            job.setSuccessRows(successCount);
            job.setErrorRows(errorCount);

            // Complete
            job.setStatus(errorCount > 0 ? ImportJob.ImportStatus.COMPLETED_WITH_ERRORS : ImportJob.ImportStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            job.setErrors(errors);
            importJobRepository.save(job);

            eventPublisher.publishCompleted(job);
            log.info("Import completed: id={}, success={}, errors={}", jobId, successCount, errorCount);

        } catch (Exception e) {
            job.setStatus(ImportJob.ImportStatus.FAILED);
            job.setCompletedAt(Instant.now());
            importJobRepository.save(job);
            eventPublisher.publishFailed(job, e.getMessage());
            log.error("Import failed: id={}", jobId, e);
        } finally {
            TenantContext.clear();
        }
    }

    private void processRow(String tenantId, Map<String, String> row, Map<String, String> fieldMapping) {
        String email = getMappedField(row, fieldMapping, "email");
        String subscriberKey = getMappedField(row, fieldMapping, "subscriberKey");

        if (subscriberKey == null || subscriberKey.isBlank()) {
            subscriberKey = email; // fallback to email as key
        }

        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email: " + email);
        }

        // Deduplication — check if exists
        Optional<Subscriber> existing = subscriberRepository
                .findByTenantIdAndSubscriberKeyAndDeletedAtIsNull(tenantId, subscriberKey);

        if (existing.isPresent()) {
            Subscriber sub = existing.get();
            applyMappedFields(sub, row, fieldMapping);
            subscriberRepository.save(sub);
        } else {
            Subscriber sub = new Subscriber();
            sub.setTenantId(tenantId);
            sub.setSubscriberKey(subscriberKey);
            sub.setEmail(email.toLowerCase().trim());
            sub.setSubscribedAt(Instant.now());
            applyMappedFields(sub, row, fieldMapping);
            subscriberRepository.save(sub);
        }
    }

    private void applyMappedFields(Subscriber sub, Map<String, String> row, Map<String, String> fieldMapping) {
        String firstName = getMappedField(row, fieldMapping, "firstName");
        String lastName = getMappedField(row, fieldMapping, "lastName");
        String phone = getMappedField(row, fieldMapping, "phone");

        if (firstName != null) sub.setFirstName(firstName);
        if (lastName != null) sub.setLastName(lastName);
        if (phone != null) sub.setPhone(phone);
    }

    private String getMappedField(Map<String, String> row, Map<String, String> mapping, String targetField) {
        String sourceField = mapping.get(targetField);
        if (sourceField == null) return null;
        Object value = row.get(sourceField);
        return value != null ? value.toString() : null;
    }
}

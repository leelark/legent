package com.legent.audience.service;

import com.legent.audience.domain.ImportJob;
import com.legent.audience.event.ImportEventPublisher;
import com.legent.audience.repository.ImportJobRepository;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.common.constant.AppConstants;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import okhttp3.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImportProcessingServiceTest {

    @AfterEach
    void tearDown() {
        com.legent.security.TenantContext.clear();
    }

    @Test
    void subscriberImportCapsRetainedErrorsAcrossChunksButCountsEveryFailedRow() throws Exception {
        ImportJobRepository importJobRepository = mock(ImportJobRepository.class);
        SubscriberRepository subscriberRepository = mock(SubscriberRepository.class);
        DataExtensionService dataExtensionService = mock(DataExtensionService.class);
        ImportEventPublisher eventPublisher = mock(ImportEventPublisher.class);
        MinioClient minioClient = mock(MinioClient.class);
        PlatformTransactionManager transactionManager = transactionManager();
        ImportJob job = importJob("job-1", "SUBSCRIBER", "imports/subscribers.csv",
                Map.of("email", "Email"));
        int rowCount = AppConstants.IMPORT_CHUNK_SIZE + AppConstants.IMPORT_MAX_ERRORS + 1;

        when(importJobRepository.findByTenantIdAndWorkspaceIdAndId("tenant-1", "workspace-1", "job-1"))
                .thenReturn(Optional.of(job));
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(minioClient.getObject(any())).thenReturn(csvObject("imports/subscribers.csv", invalidEmailCsv(rowCount)));
        ImportProcessingService service = service(
                importJobRepository,
                subscriberRepository,
                dataExtensionService,
                eventPublisher,
                transactionManager,
                minioClient);

        service.processImport("job-1", "tenant-1", "workspace-1");

        assertEquals(rowCount, job.getTotalRows());
        assertEquals(rowCount, job.getProcessedRows());
        assertEquals(0, job.getSuccessRows());
        assertEquals(rowCount, job.getErrorRows());
        assertEquals(AppConstants.IMPORT_MAX_ERRORS, job.getErrors().size());
        assertEquals(ImportJob.ImportStatus.COMPLETED_WITH_ERRORS, job.getStatus());
        verify(subscriberRepository, never()).saveAll(any());
        verify(eventPublisher).publishCompleted(job);
        verify(importJobRepository, never()).findById(anyString());
    }

    @Test
    void dataExtensionImportCapsRetainedErrorsButCountsEveryFailedRow() throws Exception {
        ImportJobRepository importJobRepository = mock(ImportJobRepository.class);
        SubscriberRepository subscriberRepository = mock(SubscriberRepository.class);
        DataExtensionService dataExtensionService = mock(DataExtensionService.class);
        ImportEventPublisher eventPublisher = mock(ImportEventPublisher.class);
        MinioClient minioClient = mock(MinioClient.class);
        PlatformTransactionManager transactionManager = transactionManager();
        ImportJob job = importJob("job-2", "DATA_EXTENSION", "imports/data-extension.csv",
                Map.of("score", "Score"));
        job.setTargetId("de-1");
        int rowCount = AppConstants.IMPORT_MAX_ERRORS + 5;

        when(importJobRepository.findByTenantIdAndWorkspaceIdAndId("tenant-1", "workspace-1", "job-2"))
                .thenReturn(Optional.of(job));
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(minioClient.getObject(any())).thenReturn(csvObject("imports/data-extension.csv", scoreCsv(rowCount)));
        when(dataExtensionService.addRecord(eq("de-1"), any())).thenThrow(new IllegalArgumentException("invalid score"));
        ImportProcessingService service = service(
                importJobRepository,
                subscriberRepository,
                dataExtensionService,
                eventPublisher,
                transactionManager,
                minioClient);

        service.processImport("job-2", "tenant-1", "workspace-1");

        assertEquals(rowCount, job.getTotalRows());
        assertEquals(rowCount, job.getErrorRows());
        assertEquals(AppConstants.IMPORT_MAX_ERRORS, job.getErrors().size());
        assertEquals(ImportJob.ImportStatus.COMPLETED_WITH_ERRORS, job.getStatus());
        verify(subscriberRepository, never()).saveAll(any());
        verify(eventPublisher).publishCompleted(job);
        verify(importJobRepository, never()).findById(anyString());
    }

    @Test
    void processImportReturnsBeforeSideEffectsWhenScopedJobIsMissing() {
        ImportJobRepository importJobRepository = mock(ImportJobRepository.class);
        SubscriberRepository subscriberRepository = mock(SubscriberRepository.class);
        DataExtensionService dataExtensionService = mock(DataExtensionService.class);
        ImportEventPublisher eventPublisher = mock(ImportEventPublisher.class);
        MinioClient minioClient = mock(MinioClient.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(importJobRepository.findByTenantIdAndWorkspaceIdAndId("tenant-1", "workspace-1", "foreign-job"))
                .thenReturn(Optional.empty());
        ImportProcessingService service = service(
                importJobRepository,
                subscriberRepository,
                dataExtensionService,
                eventPublisher,
                transactionManager,
                minioClient);
        com.legent.security.TenantContext.setTenantId("existing-tenant");
        com.legent.security.TenantContext.setWorkspaceId("existing-workspace");

        service.processImport("foreign-job", "tenant-1", "workspace-1");

        assertEquals("existing-tenant", com.legent.security.TenantContext.getTenantId());
        assertEquals("existing-workspace", com.legent.security.TenantContext.getWorkspaceId());
        verify(importJobRepository).findByTenantIdAndWorkspaceIdAndId("tenant-1", "workspace-1", "foreign-job");
        verify(importJobRepository, never()).findById(anyString());
        verify(importJobRepository, never()).save(any());
        verifyNoInteractions(subscriberRepository, dataExtensionService, eventPublisher, minioClient, transactionManager);
    }

    private ImportProcessingService service(ImportJobRepository importJobRepository,
                                            SubscriberRepository subscriberRepository,
                                            DataExtensionService dataExtensionService,
                                            ImportEventPublisher eventPublisher,
                                            PlatformTransactionManager transactionManager,
                                            MinioClient minioClient) {
        ImportProcessingService service = new ImportProcessingService(
                importJobRepository,
                subscriberRepository,
                dataExtensionService,
                eventPublisher,
                transactionManager,
                minioClient);
        ReflectionTestUtils.setField(service, "bucket", "imports");
        return service;
    }

    private PlatformTransactionManager transactionManager() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        return transactionManager;
    }

    private ImportJob importJob(String id, String targetType, String fileName, Map<String, String> mapping) {
        ImportJob job = new ImportJob();
        job.setId(id);
        job.setTenantId("tenant-1");
        job.setWorkspaceId("workspace-1");
        job.setFileName(fileName);
        job.setTargetType(targetType);
        job.setFieldMapping(mapping);
        return job;
    }

    private GetObjectResponse csvObject(String objectName, String csv) {
        return new GetObjectResponse(
                Headers.of(),
                "imports",
                "us-east-1",
                objectName,
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    private String invalidEmailCsv(int rowCount) {
        StringBuilder csv = new StringBuilder("Email\n");
        for (int i = 0; i < rowCount; i++) {
            csv.append("invalid-").append(i).append('\n');
        }
        return csv.toString();
    }

    private String scoreCsv(int rowCount) {
        StringBuilder csv = new StringBuilder("Score\n");
        for (int i = 0; i < rowCount; i++) {
            csv.append(i).append('\n');
        }
        return csv.toString();
    }
}

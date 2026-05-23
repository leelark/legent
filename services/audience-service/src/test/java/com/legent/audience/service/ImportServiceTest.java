package com.legent.audience.service;

import com.legent.audience.domain.ImportJob;
import com.legent.audience.dto.ImportDto;
import com.legent.audience.event.ImportEventPublisher;
import com.legent.audience.repository.ImportJobRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportServiceTest {

    @Test
    void validateImportFileAcceptsCsvOnly() {
        ImportService service = service();
        MockMultipartFile csv = new MockMultipartFile(
                "file",
                "subscribers.csv",
                "text/csv",
                "email\nada@example.com".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        assertDoesNotThrow(() -> service.validateImportFile(csv));
    }

    @Test
    void validateImportFileRejectsExecutablePayload() {
        ImportService service = service();
        MockMultipartFile executable = new MockMultipartFile(
                "file",
                "subscribers.exe",
                "application/x-msdownload",
                new byte[] {1, 2, 3}
        );

        assertThrows(IllegalArgumentException.class, () -> service.validateImportFile(executable));
    }

    @Test
    void validateImportFileRejectsOversizedPayload() {
        ImportService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "subscribers.csv",
                "text/csv",
                new byte[] {1, 2, 3, 4}
        );
        ReflectionTestUtils.setField(service, "maxImportFileSizeBytes", 2L);

        assertThrows(IllegalArgumentException.class, () -> service.validateImportFile(file));
    }

    @Test
    void startImportAllowsDataExtensionTargetWithoutEmailMapping() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        try {
            ImportJobRepository repository = mock(ImportJobRepository.class);
            ImportProcessingService processingService = mock(ImportProcessingService.class);
            ImportEventPublisher publisher = mock(ImportEventPublisher.class);
            when(repository.save(any(ImportJob.class))).thenAnswer(invocation -> {
                ImportJob job = invocation.getArgument(0);
                job.setId("import-1");
                return job;
            });
            ImportService service = new ImportService(repository, processingService, publisher, null);

            ImportDto.StatusResponse response = service.startImport(ImportDto.StartRequest.builder()
                    .fileName("import_123.csv")
                    .targetType("DATA_EXTENSION")
                    .targetId("de-1")
                    .fieldMapping(Map.of("score", "Score"))
                    .build());

            assertEquals("DATA_EXTENSION", response.getTargetType());
            verify(processingService).processImport("import-1", "tenant-1", "workspace-1");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void startImportStillRequiresEmailMappingForSubscribers() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        try {
            ImportService service = new ImportService(null, null, null, null);

            assertThrows(IllegalArgumentException.class, () -> service.startImport(ImportDto.StartRequest.builder()
                    .fileName("import_123.csv")
                    .targetType("SUBSCRIBER")
                    .fieldMapping(Map.of("firstName", "First Name"))
                    .build()));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void startImportRejectsRawUrlObjectKey() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        try {
            ImportService service = new ImportService(null, null, null, null);

            assertThrows(IllegalArgumentException.class, () -> service.startImport(ImportDto.StartRequest.builder()
                    .fileName("https://storage.example.com/import.csv")
                    .targetType("SUBSCRIBER")
                    .fieldMapping(Map.of("email", "Email"))
                    .build()));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void startImportAcceptsAutomationScopedArtifactObjectKey() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        try {
            ImportJobRepository repository = mock(ImportJobRepository.class);
            ImportProcessingService processingService = mock(ImportProcessingService.class);
            ImportEventPublisher publisher = mock(ImportEventPublisher.class);
            when(repository.save(any(ImportJob.class))).thenAnswer(invocation -> {
                ImportJob job = invocation.getArgument(0);
                job.setId("import-1");
                return job;
            });
            ImportService service = new ImportService(repository, processingService, publisher, null);

            ImportDto.StatusResponse response = service.startImport(ImportDto.StartRequest.builder()
                    .fileName("tenants/tenant-1/workspaces/workspace-1/automation-artifacts/artifact-1/import.csv")
                    .targetType("SUBSCRIBER")
                    .fieldMapping(Map.of("email", "Email"))
                    .build());

            assertEquals("SUBSCRIBER", response.getTargetType());
            verify(processingService).processImport("import-1", "tenant-1", "workspace-1");
        } finally {
            TenantContext.clear();
        }
    }

    private ImportService service() {
        ImportService service = new ImportService(null, null, null, null);
        ReflectionTestUtils.setField(service, "maxImportFileSizeBytes", 1024L);
        ReflectionTestUtils.setField(service, "allowedImportContentTypes", "text/csv,application/csv,application/vnd.ms-excel,text/plain");
        return service;
    }
}

package com.legent.audience.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private ImportService service() {
        ImportService service = new ImportService(null, null, null, null);
        ReflectionTestUtils.setField(service, "maxImportFileSizeBytes", 1024L);
        ReflectionTestUtils.setField(service, "allowedImportContentTypes", "text/csv,application/csv,application/vnd.ms-excel,text/plain");
        return service;
    }
}

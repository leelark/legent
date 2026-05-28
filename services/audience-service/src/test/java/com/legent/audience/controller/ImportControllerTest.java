package com.legent.audience.controller;

import com.legent.audience.dto.ImportDto;
import com.legent.audience.service.ImportService;
import com.legent.common.security.InternalServiceIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImportControllerTest {

    private static final String INTERNAL_TOKEN = "internal-service-token-1234567890abcdef";

    @Test
    void startInternalImportRequiresSignedAutomationIdentity() {
        ImportService service = mock(ImportService.class);
        ImportController controller = new ImportController(service);
        ReflectionTestUtils.setField(controller, "internalApiToken", INTERNAL_TOKEN);
        ImportDto.StartRequest request = ImportDto.StartRequest.builder()
                .fileName("contacts.csv")
                .targetType("SUBSCRIBER")
                .fieldMapping(Map.of("email", "email"))
                .build();
        ImportDto.StatusResponse expected = ImportDto.StatusResponse.builder()
                .id("import-1")
                .status("QUEUED")
                .build();
        when(service.startImport(same(request))).thenReturn(expected);
        Instant timestamp = Instant.now();

        var response = controller.startInternalImport(
                INTERNAL_TOKEN,
                "automation-service",
                timestamp.toString(),
                signature("tenant-1", "workspace-1", timestamp),
                "tenant-1",
                "workspace-1",
                request);

        assertThat(response.getData()).isSameAs(expected);
        verify(service).startImport(request);
    }

    @Test
    void startInternalImportRejectsUnsignedInternalTokenBeforeServiceAccess() {
        ImportService service = mock(ImportService.class);
        ImportController controller = new ImportController(service);
        ReflectionTestUtils.setField(controller, "internalApiToken", INTERNAL_TOKEN);

        assertThatThrownBy(() -> controller.startInternalImport(
                INTERNAL_TOKEN,
                "automation-service",
                null,
                null,
                "tenant-1",
                "workspace-1",
                ImportDto.StartRequest.builder()
                        .fileName("contacts.csv")
                        .fieldMapping(Map.of("email", "email"))
                        .build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(service);
    }

    private String signature(String tenantId, String workspaceId, Instant timestamp) {
        return InternalServiceIdentity.sign(
                INTERNAL_TOKEN,
                "automation-service",
                tenantId,
                workspaceId,
                InternalServiceIdentity.ACTION_AUDIENCE_IMPORT_START,
                timestamp);
    }
}

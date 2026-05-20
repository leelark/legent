package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.AdminSettingsDto;
import com.legent.foundation.repository.ConfigRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AdminSettingsServiceTest {

    @Mock
    private ConfigService configService;

    @Mock
    private ConfigRepository configRepository;

    @Mock
    private ConfigVersioningService configVersioningService;

    @Mock
    private AdminOperationsService adminOperationsService;

    private AdminSettingsService service;

    @BeforeEach
    void setUp() {
        service = new AdminSettingsService(
                configService,
                configRepository,
                configVersioningService,
                adminOperationsService,
                new ObjectMapper()
        );
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setEnvironmentId("local");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void validate_rejectsUnsupportedTypeAndScopeBeforeApply() {
        AdminSettingsDto.ApplyRequest request = new AdminSettingsDto.ApplyRequest();
        request.setKey("delivery.max-retries");
        request.setValue("10");
        request.setType("integerish");
        request.setScope("workspace-ish");

        AdminSettingsDto.ValidateResponse response = service.validate(request);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getErrors()).contains(
                "Unsupported setting type: INTEGERISH",
                "Unsupported setting scope: WORKSPACE-ISH"
        );
    }

    @Test
    void apply_doesNotReachConfigServiceWhenTypeIsUnsupported() {
        AdminSettingsDto.ApplyRequest request = new AdminSettingsDto.ApplyRequest();
        request.setKey("delivery.max-retries");
        request.setValue("10");
        request.setType("integerish");
        request.setScope("WORKSPACE");

        assertThatThrownBy(() -> service.apply(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported setting type: INTEGERISH");
        verifyNoInteractions(configService);
    }

    @Test
    void validate_rejectsWorkspaceAndEnvironmentContextMismatch() {
        AdminSettingsDto.ApplyRequest request = new AdminSettingsDto.ApplyRequest();
        request.setKey("delivery.max-retries");
        request.setValue("10");
        request.setScope("ENVIRONMENT");
        request.setWorkspaceId("workspace-2");
        request.setEnvironmentId("prod");

        AdminSettingsDto.ValidateResponse response = service.validate(request);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getErrors()).contains(
                "workspaceId does not match the current workspace",
                "environmentId does not match the current environment"
        );
    }

    @Test
    void apply_doesNotReachConfigServiceWhenWorkspaceContextMismatches() {
        AdminSettingsDto.ApplyRequest request = new AdminSettingsDto.ApplyRequest();
        request.setKey("delivery.max-retries");
        request.setValue("10");
        request.setScope("WORKSPACE");
        request.setWorkspaceId("workspace-2");

        assertThatThrownBy(() -> service.apply(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId does not match the current workspace");
        verifyNoInteractions(configService);
    }

    @Test
    void reset_rejectsUnsupportedScopeBeforeLookup() {
        AdminSettingsDto.ResetRequest request = new AdminSettingsDto.ResetRequest();
        request.setKey("delivery.max-retries");
        request.setScope("workspace-ish");

        assertThatThrownBy(() -> service.reset(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported setting scope: WORKSPACE-ISH");
        verifyNoInteractions(configRepository);
    }

    @Test
    void reset_rejectsWorkspaceContextMismatchBeforeLookup() {
        AdminSettingsDto.ResetRequest request = new AdminSettingsDto.ResetRequest();
        request.setKey("delivery.max-retries");
        request.setScope("WORKSPACE");
        request.setWorkspaceId("workspace-2");

        assertThatThrownBy(() -> service.reset(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId does not match the current workspace");
        verifyNoInteractions(configRepository);
    }
}

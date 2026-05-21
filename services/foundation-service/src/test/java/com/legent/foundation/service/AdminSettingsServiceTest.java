package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.domain.ConfigVersionHistory;
import com.legent.foundation.dto.AdminSettingsDto;
import com.legent.foundation.dto.ConfigDto;
import com.legent.foundation.repository.ConfigRepository;
import com.legent.security.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSettingsServiceTest {

    @Mock
    private ConfigRepository configRepository;

    @Mock
    private ConfigVersioningService configVersioningService;

    @Mock
    private ConfigService configService;

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
        verifyNoInteractions(configService, configRepository, configVersioningService, adminOperationsService);
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
        verifyNoInteractions(configService, configRepository, configVersioningService, adminOperationsService);
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

    @Test
    void history_passesCurrentWorkspaceEnvironmentForSpecificKey() {
        ConfigVersionHistory entry = new ConfigVersionHistory();
        when(configVersioningService.getConfigVersionHistory(
                "tenant-1", "workspace-1", "local", "delivery.max-retries"))
                .thenReturn(List.of(entry));

        List<ConfigVersionHistory> result = service.history("delivery.max-retries");

        assertThat(result).containsExactly(entry);
        verify(configVersioningService).getConfigVersionHistory(
                "tenant-1", "workspace-1", "local", "delivery.max-retries");
        verifyNoInteractions(configRepository, adminOperationsService);
    }

    @Test
    void history_passesCurrentWorkspaceEnvironmentForScopedTenantHistory() {
        ConfigVersionHistory entry = new ConfigVersionHistory();
        PageRequest page = PageRequest.of(0, 200);
        when(configVersioningService.getTenantVersionHistory(
                "tenant-1", "workspace-1", "local", page))
                .thenReturn(new PageImpl<>(List.of(entry), page, 1));

        List<ConfigVersionHistory> result = service.history(null);

        assertThat(result).containsExactly(entry);
        verify(configVersioningService).getTenantVersionHistory(
                "tenant-1", "workspace-1", "local", page);
        verifyNoInteractions(configRepository, adminOperationsService);
    }

    @Test
    void rollback_passesCurrentWorkspaceEnvironmentToVersioningService() {
        ConfigDto.Response rolled = ConfigDto.Response.builder()
                .configKey("delivery.max-retries")
                .configValue("5")
                .valueType("INTEGER")
                .scopeType("ENVIRONMENT")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .environmentId("local")
                .moduleKey("delivery")
                .category("DELIVERY")
                .configVersion(3)
                .build();
        when(configVersioningService.rollbackConfig(
                "tenant-1", "workspace-1", "local", "delivery.max-retries", 2))
                .thenReturn(rolled);

        AdminSettingsDto.Entry result = service.rollback("delivery.max-retries", 2);

        assertThat(result.getKey()).isEqualTo("delivery.max-retries");
        assertThat(result.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(result.getEnvironmentId()).isEqualTo("local");
        verify(configVersioningService).rollbackConfig(
                "tenant-1", "workspace-1", "local", "delivery.max-retries", 2);
        verify(adminOperationsService).recordSyncEvent(
                eq("CONFIG_ROLLED_BACK"),
                eq("admin-settings"),
                eq(List.of("delivery", "system")),
                anyMap());
    }
}

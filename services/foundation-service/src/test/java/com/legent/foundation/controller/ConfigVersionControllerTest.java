package com.legent.foundation.controller;

import com.legent.foundation.domain.ConfigVersionHistory;
import com.legent.foundation.dto.ConfigDto;
import com.legent.foundation.service.ConfigVersioningService;
import com.legent.security.TenantContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigVersionControllerTest {

    @Mock
    private ConfigVersioningService configVersioningService;

    private ConfigVersionController controller;

    @BeforeEach
    void setUp() {
        controller = new ConfigVersionController(configVersioningService);
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setEnvironmentId("local");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getConfigVersionHistory_passesCurrentWorkspaceEnvironment() {
        when(configVersioningService.getConfigVersionHistory(
                "tenant-1", "workspace-1", "local", "delivery.max-retries"))
                .thenReturn(List.of(new ConfigVersionHistory()));

        controller.getConfigVersionHistory("delivery.max-retries");

        verify(configVersioningService).getConfigVersionHistory(
                "tenant-1", "workspace-1", "local", "delivery.max-retries");
    }

    @Test
    void getConfigVersion_passesCurrentWorkspaceEnvironment() {
        when(configVersioningService.getConfigVersion(
                "tenant-1", "workspace-1", "local", "delivery.max-retries", 2))
                .thenReturn(new ConfigVersionHistory());

        controller.getConfigVersion("delivery.max-retries", 2);

        verify(configVersioningService).getConfigVersion(
                "tenant-1", "workspace-1", "local", "delivery.max-retries", 2);
    }

    @Test
    void getAllVersionHistory_passesCurrentWorkspaceEnvironment() {
        PageRequest page = PageRequest.of(1, 50);
        when(configVersioningService.getTenantVersionHistory(
                "tenant-1", "workspace-1", "local", page))
                .thenReturn(new PageImpl<>(List.of(new ConfigVersionHistory()), page, 1));

        controller.getAllVersionHistory(1, 50);

        verify(configVersioningService).getTenantVersionHistory(
                "tenant-1", "workspace-1", "local", page);
    }

    @Test
    void rollbackConfig_passesCurrentWorkspaceEnvironment() {
        ConfigDto.Response response = ConfigDto.Response.builder()
                .configKey("delivery.max-retries")
                .build();
        when(configVersioningService.rollbackConfig(
                "tenant-1", "workspace-1", "local", "delivery.max-retries", 2))
                .thenReturn(response);

        controller.rollbackConfig("delivery.max-retries", 2);

        verify(configVersioningService).rollbackConfig(
                "tenant-1", "workspace-1", "local", "delivery.max-retries", 2);
    }

    @Test
    void compareVersions_passesCurrentWorkspaceEnvironment() {
        when(configVersioningService.compareVersions(
                "tenant-1", "workspace-1", "local", "delivery.max-retries", 1, 2))
                .thenReturn(Map.of("areEqual", false));

        controller.compareVersions("delivery.max-retries", 1, 2);

        verify(configVersioningService).compareVersions(
                "tenant-1", "workspace-1", "local", "delivery.max-retries", 1, 2);
    }
}

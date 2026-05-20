package com.legent.delivery.controller;

import com.legent.delivery.adapter.impl.SmtpProviderAdapter;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.dto.SmtpProviderDto;
import com.legent.delivery.repository.SmtpProviderRepository;
import com.legent.delivery.service.CredentialEncryptionService;
import com.legent.delivery.service.ProviderHealthMonitoringService;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderControllerWorkspaceScopeTest {

    private SmtpProviderRepository repository;
    private CredentialEncryptionService encryptionService;
    private SmtpProviderAdapter smtpProviderAdapter;
    private ProviderHealthMonitoringService healthMonitoringService;
    private ProviderController controller;

    @BeforeEach
    void setUp() {
        repository = mock(SmtpProviderRepository.class);
        encryptionService = mock(CredentialEncryptionService.class);
        smtpProviderAdapter = mock(SmtpProviderAdapter.class);
        healthMonitoringService = mock(ProviderHealthMonitoringService.class);
        controller = new ProviderController(repository, encryptionService, smtpProviderAdapter, healthMonitoringService);
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-a");
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void list_usesTenantAndWorkspaceScope() {
        SmtpProvider provider = provider("provider-a", "workspace-a");
        when(repository.findByTenantIdAndWorkspaceIdAndIsActiveTrueAndDeletedAtIsNullOrderByPriorityAsc("tenant-1", "workspace-a"))
                .thenReturn(List.of(provider));

        var response = controller.list(false);

        assertEquals(1, response.getData().size());
        assertEquals("provider-a", response.getData().getFirst().getId());
        assertEquals("workspace-a", response.getData().getFirst().getWorkspaceId());
    }

    @Test
    void listIncludingInactive_usesTenantAndWorkspaceScope() {
        SmtpProvider provider = provider("provider-a", "workspace-a");
        provider.setActive(false);
        when(repository.findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByPriorityAsc("tenant-1", "workspace-a"))
                .thenReturn(List.of(provider));

        var response = controller.list(true);

        assertEquals(1, response.getData().size());
        assertEquals("provider-a", response.getData().getFirst().getId());
    }

    @Test
    void health_usesTenantAndWorkspaceScope() {
        SmtpProvider provider = provider("provider-a", "workspace-a");
        when(repository.findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByPriorityAsc("tenant-1", "workspace-a"))
                .thenReturn(List.of(provider));

        var response = controller.health();

        assertEquals(1, response.getData().size());
        assertEquals("provider-a", response.getData().getFirst().getId());
    }

    @Test
    void create_stampsTenantAndWorkspaceFromContext() {
        when(repository.save(any(SmtpProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SmtpProviderDto.CreateRequest request = SmtpProviderDto.CreateRequest.builder()
                .name("Primary SMTP")
                .type("SMTP")
                .host("smtp.example.com")
                .port(587)
                .build();

        var response = controller.create(request);

        assertEquals("workspace-a", response.getData().getWorkspaceId());
        verify(repository).save(argThat(provider ->
                "tenant-1".equals(provider.getTenantId())
                        && "workspace-a".equals(provider.getWorkspaceId())
                        && "Primary SMTP".equals(provider.getName())));
    }

    @Test
    void update_deniesSameTenantProviderFromSiblingWorkspace() {
        SmtpProviderDto.CreateRequest request = SmtpProviderDto.CreateRequest.builder()
                .name("Updated")
                .type("SMTP")
                .build();
        when(repository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("provider-b", "tenant-1", "workspace-a"))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> controller.update("provider-b", request));

        verify(repository, never()).save(any());
    }

    @Test
    void delete_deniesSameTenantProviderFromSiblingWorkspace() {
        when(repository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("provider-b", "tenant-1", "workspace-a"))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> controller.delete("provider-b"));

        verify(repository, never()).delete(any());
        verify(smtpProviderAdapter, never()).invalidateCache(any());
    }

    @Test
    void testProvider_deniesSameTenantProviderFromSiblingWorkspace() {
        when(repository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("provider-b", "tenant-1", "workspace-a"))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> controller.testProvider("provider-b"));

        verify(healthMonitoringService, never()).checkProviderHealth(any());
    }

    @Test
    void list_whenWorkspaceMissing_failsClosed() {
        TenantContext.clear();
        TenantContext.setTenantId("tenant-1");

        assertThrows(IllegalStateException.class, () -> controller.list(false));

        verify(repository, never()).findByTenantIdAndWorkspaceIdAndIsActiveTrueAndDeletedAtIsNullOrderByPriorityAsc(any(), any());
    }

    private SmtpProvider provider(String id, String workspaceId) {
        SmtpProvider provider = new SmtpProvider();
        provider.setId(id);
        provider.setTenantId("tenant-1");
        provider.setWorkspaceId(workspaceId);
        provider.setName("Primary SMTP");
        provider.setType("SMTP");
        provider.setHost("smtp.example.com");
        provider.setActive(true);
        provider.setPriority(1);
        return provider;
    }
}

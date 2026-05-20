package com.legent.delivery.service;

import com.legent.delivery.domain.ProviderHealthCheck;
import com.legent.delivery.domain.ProviderHealthStatus;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.repository.ProviderHealthCheckRepository;
import com.legent.delivery.repository.ProviderHealthStatusRepository;
import com.legent.delivery.repository.SmtpProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderHealthMonitoringServiceTest {

    @Mock private ProviderHealthCheckRepository healthCheckRepository;
    @Mock private ProviderHealthStatusRepository healthStatusRepository;
    @Mock private SmtpProviderRepository providerRepository;
    @Mock private ProviderCircuitBreaker circuitBreaker;

    private ProviderHealthMonitoringService service;

    @BeforeEach
    void setUp() {
        service = new ProviderHealthMonitoringService(
                healthCheckRepository,
                healthStatusRepository,
                providerRepository,
                circuitBreaker);
    }

    @Test
    void checkProviderHealth_usesProviderWorkspaceForHistoryAndStatus() {
        SmtpProvider provider = provider("provider-1", "workspace-a");
        provider.setActive(false);
        Instant since = anyInstant();
        when(healthCheckRepository.countTotalChecks(eq("tenant-1"), eq("workspace-a"), eq("provider-1"), any()))
                .thenReturn(4L);
        when(healthCheckRepository.countHealthyChecks(eq("tenant-1"), eq("workspace-a"), eq("provider-1"), any()))
                .thenReturn(3L);
        when(healthCheckRepository.findByTenantIdAndWorkspaceIdAndProviderIdOrderByCheckTimestampDesc(
                "tenant-1",
                "workspace-a",
                "provider-1"))
                .thenReturn(List.of());
        when(healthStatusRepository.findByTenantIdAndWorkspaceIdAndProviderId("tenant-1", "workspace-a", "provider-1"))
                .thenReturn(Optional.empty());
        when(circuitBreaker.isCircuitClosed("provider-1")).thenReturn(true);

        service.checkProviderHealth(provider);

        verify(healthCheckRepository).save(org.mockito.ArgumentMatchers.argThat(check ->
                "tenant-1".equals(check.getTenantId())
                        && "workspace-a".equals(check.getWorkspaceId())
                        && "provider-1".equals(check.getProviderId())));
        verify(healthStatusRepository).save(org.mockito.ArgumentMatchers.argThat(status ->
                "tenant-1".equals(status.getTenantId())
                        && "workspace-a".equals(status.getWorkspaceId())
                        && "provider-1".equals(status.getProviderId())));
        verify(providerRepository).save(provider);
    }

    @Test
    void checkProviderHealth_whenProviderWorkspaceMissing_failsClosed() {
        SmtpProvider provider = provider("provider-1", null);

        assertThrows(IllegalStateException.class, () -> service.checkProviderHealth(provider));

        verify(healthCheckRepository, never()).save(any());
        verify(healthStatusRepository, never()).save(any());
    }

    @Test
    void getLatestHealthCheck_usesTenantWorkspaceProviderScope() {
        ProviderHealthCheck check = new ProviderHealthCheck();
        when(healthCheckRepository.findFirstByTenantIdAndWorkspaceIdAndProviderIdOrderByCheckTimestampDesc(
                "tenant-1",
                "workspace-a",
                "provider-1"))
                .thenReturn(Optional.of(check));

        assertEquals(Optional.of(check), service.getLatestHealthCheck("tenant-1", "workspace-a", "provider-1"));
    }

    private SmtpProvider provider(String providerId, String workspaceId) {
        SmtpProvider provider = new SmtpProvider();
        provider.setId(providerId);
        provider.setTenantId("tenant-1");
        provider.setWorkspaceId(workspaceId);
        provider.setName("Provider");
        provider.setType("SMTP");
        provider.setHost("smtp.example.com");
        provider.setPort(587);
        provider.setHealthCheckEnabled(true);
        return provider;
    }

    private Instant anyInstant() {
        return Instant.now();
    }
}

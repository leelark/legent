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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    void performHealthChecks_readsActiveProvidersInBoundedDeterministicPages() {
        PageRequest pageRequest = PageRequest.of(0, ProviderHealthMonitoringService.ACTIVE_PROVIDER_HEALTH_CHECK_PAGE_SIZE);
        SmtpProvider provider1 = provider("provider-001", "workspace-a");
        provider1.setType("MOCK");
        SmtpProvider disabledProvider = provider("provider-002", "workspace-a");
        disabledProvider.setHealthCheckEnabled(false);
        List<SmtpProvider> firstPage = new ArrayList<>();
        firstPage.add(provider1);
        firstPage.add(disabledProvider);
        for (int index = 3; index <= ProviderHealthMonitoringService.ACTIVE_PROVIDER_HEALTH_CHECK_PAGE_SIZE; index++) {
            SmtpProvider disabledPageProvider = provider("provider-%03d".formatted(index), "workspace-a");
            disabledPageProvider.setHealthCheckEnabled(false);
            firstPage.add(disabledPageProvider);
        }
        SmtpProvider providerAfterFirstPage = provider("provider-101", "workspace-b");
        providerAfterFirstPage.setType("MOCK");
        when(providerRepository.findActiveProvidersAfterId(isNull(), eq(pageRequest)))
                .thenReturn(firstPage);
        when(providerRepository.findActiveProvidersAfterId("provider-100", pageRequest))
                .thenReturn(List.of(providerAfterFirstPage));
        stubHealthDependencies(provider1, List.of());
        stubHealthDependencies(providerAfterFirstPage, List.of());

        service.performHealthChecks();

        ArgumentCaptor<String> lastProviderIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(providerRepository, times(2)).findActiveProvidersAfterId(
                lastProviderIdCaptor.capture(),
                pageableCaptor.capture());
        assertEquals(Arrays.asList(null, "provider-100"), lastProviderIdCaptor.getAllValues());
        assertEquals(0, pageableCaptor.getAllValues().get(0).getPageNumber());
        assertEquals(ProviderHealthMonitoringService.ACTIVE_PROVIDER_HEALTH_CHECK_PAGE_SIZE,
                pageableCaptor.getAllValues().get(0).getPageSize());
        assertEquals(0, pageableCaptor.getAllValues().get(1).getPageNumber());
        assertEquals(ProviderHealthMonitoringService.ACTIVE_PROVIDER_HEALTH_CHECK_PAGE_SIZE,
                pageableCaptor.getAllValues().get(1).getPageSize());
        verify(providerRepository).save(provider1);
        verify(providerRepository).save(providerAfterFirstPage);
        verify(providerRepository, never()).save(disabledProvider);
    }

    @Test
    void checkProviderHealth_usesProviderWorkspaceForHistoryAndStatus() {
        SmtpProvider provider = provider("provider-1", "workspace-a");
        provider.setActive(false);
        when(healthCheckRepository.countTotalChecks(eq("tenant-1"), eq("workspace-a"), eq("provider-1"), any()))
                .thenReturn(4L);
        when(healthCheckRepository.countHealthyChecks(eq("tenant-1"), eq("workspace-a"), eq("provider-1"), any()))
                .thenReturn(3L);
        when(healthCheckRepository.findByTenantIdAndWorkspaceIdAndProviderIdOrderByCheckTimestampDescIdDesc(
                "tenant-1",
                "workspace-a",
                "provider-1",
                PageRequest.of(0, ProviderHealthMonitoringService.CONSECUTIVE_FAILURE_HISTORY_LIMIT)))
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
        provider.setActive(false);

        assertThrows(IllegalStateException.class, () -> service.checkProviderHealth(provider));

        verify(healthCheckRepository, never()).save(any());
        verify(healthStatusRepository, never()).save(any());
    }

    @Test
    void checkProviderHealth_readsBoundedRecentChecksNewestFirstForConsecutiveFailures() {
        SmtpProvider provider = provider("provider-1", "workspace-a");
        provider.setActive(false);
        ProviderHealthCheck firstFailure = healthCheck(ProviderHealthCheck.HealthStatus.UNHEALTHY);
        ProviderHealthCheck secondFailure = healthCheck(ProviderHealthCheck.HealthStatus.DEGRADED);
        ProviderHealthCheck olderSuccess = healthCheck(ProviderHealthCheck.HealthStatus.HEALTHY);
        when(healthCheckRepository.countTotalChecks(eq("tenant-1"), eq("workspace-a"), eq("provider-1"), any()))
                .thenReturn(3L);
        when(healthCheckRepository.countHealthyChecks(eq("tenant-1"), eq("workspace-a"), eq("provider-1"), any()))
                .thenReturn(1L);
        when(healthCheckRepository.findByTenantIdAndWorkspaceIdAndProviderIdOrderByCheckTimestampDescIdDesc(
                eq("tenant-1"),
                eq("workspace-a"),
                eq("provider-1"),
                any(Pageable.class)))
                .thenReturn(List.of(firstFailure, secondFailure, olderSuccess));
        when(healthStatusRepository.findByTenantIdAndWorkspaceIdAndProviderId("tenant-1", "workspace-a", "provider-1"))
                .thenReturn(Optional.empty());
        when(circuitBreaker.isCircuitClosed("provider-1")).thenReturn(true);

        service.checkProviderHealth(provider);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(healthCheckRepository).findByTenantIdAndWorkspaceIdAndProviderIdOrderByCheckTimestampDescIdDesc(
                eq("tenant-1"),
                eq("workspace-a"),
                eq("provider-1"),
                pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(ProviderHealthMonitoringService.CONSECUTIVE_FAILURE_HISTORY_LIMIT,
                pageableCaptor.getValue().getPageSize());
        verify(healthCheckRepository).save(org.mockito.ArgumentMatchers.argThat(check ->
                Integer.valueOf(2).equals(check.getConsecutiveFailures())));
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

    private void stubHealthDependencies(SmtpProvider provider, List<ProviderHealthCheck> recentChecks) {
        when(healthCheckRepository.countTotalChecks(eq(provider.getTenantId()), eq(provider.getWorkspaceId()), eq(provider.getId()), any()))
                .thenReturn(0L);
        when(healthCheckRepository.countHealthyChecks(eq(provider.getTenantId()), eq(provider.getWorkspaceId()), eq(provider.getId()), any()))
                .thenReturn(0L);
        when(healthCheckRepository.findByTenantIdAndWorkspaceIdAndProviderIdOrderByCheckTimestampDescIdDesc(
                provider.getTenantId(),
                provider.getWorkspaceId(),
                provider.getId(),
                PageRequest.of(0, ProviderHealthMonitoringService.CONSECUTIVE_FAILURE_HISTORY_LIMIT)))
                .thenReturn(recentChecks);
        when(healthStatusRepository.findByTenantIdAndWorkspaceIdAndProviderId(
                provider.getTenantId(),
                provider.getWorkspaceId(),
                provider.getId()))
                .thenReturn(Optional.empty());
        when(circuitBreaker.isCircuitClosed(provider.getId())).thenReturn(true);
    }

    private ProviderHealthCheck healthCheck(ProviderHealthCheck.HealthStatus status) {
        ProviderHealthCheck check = new ProviderHealthCheck();
        check.setStatus(status);
        return check;
    }
}

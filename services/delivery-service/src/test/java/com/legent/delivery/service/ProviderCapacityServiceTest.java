package com.legent.delivery.service;

import com.legent.delivery.domain.ProviderCapacityProfile;
import com.legent.delivery.domain.ProviderHealthStatus;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.repository.MessageLogRepository;
import com.legent.delivery.repository.ProviderCapacityProfileRepository;
import com.legent.delivery.repository.ProviderFailoverTestRepository;
import com.legent.delivery.repository.ProviderHealthStatusRepository;
import com.legent.delivery.repository.SmtpProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderCapacityServiceTest {

    @Mock private ProviderCapacityProfileRepository capacityProfileRepository;
    @Mock private ProviderHealthStatusRepository providerHealthStatusRepository;
    @Mock private ProviderFailoverTestRepository failoverTestRepository;
    @Mock private MessageLogRepository messageLogRepository;
    @Mock private DeliveryOperationsService deliveryOperationsService;
    @Mock private SmtpProviderRepository smtpProviderRepository;

    private ProviderCapacityService service;

    @BeforeEach
    void setUp() {
        service = new ProviderCapacityService(
                capacityProfileRepository,
                providerHealthStatusRepository,
                failoverTestRepository,
                messageLogRepository,
                deliveryOperationsService,
                smtpProviderRepository);
    }

    @Test
    void recommendedPerMinute_reducesCapacityWhenHealthAndSignalsAreBad() {
        ProviderCapacityProfile profile = new ProviderCapacityProfile();
        profile.setHourlyCap(6000);
        profile.setCurrentMaxPerMinute(100);
        profile.setMinSuccessRate(0.98);
        profile.setObservedSuccessRate(0.80);
        profile.setBounceRate(0.08);
        profile.setComplaintRate(0.003);
        profile.setBackpressureScore(60);

        ProviderHealthStatus health = new ProviderHealthStatus();
        health.setCurrentStatus(ProviderHealthStatus.HealthStatus.DEGRADED);

        int recommended = service.recommendedPerMinute(profile, health, 80);

        assertThat(recommended).isLessThan(15);
        assertThat(recommended).isGreaterThanOrEqualTo(1);
    }

    @Test
    void recommendedPerMinute_keepsCapacityOpenForGoodSignals() {
        ProviderCapacityProfile profile = new ProviderCapacityProfile();
        profile.setHourlyCap(6000);
        profile.setCurrentMaxPerMinute(100);
        profile.setMinSuccessRate(0.95);
        profile.setObservedSuccessRate(0.99);
        profile.setBounceRate(0.001);
        profile.setComplaintRate(0.0);
        profile.setBackpressureScore(0);

        int recommended = service.recommendedPerMinute(profile, null, 0);

        assertThat(recommended).isGreaterThanOrEqualTo(95);
    }

    @Test
    void upsert_rejectsProviderOutsideWorkspace() {
        when(smtpProviderRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                "provider-b", "tenant-1", "workspace-a"))
                .thenReturn(Optional.empty());

        ProviderCapacityService.ProviderCapacityRequest request = new ProviderCapacityService.ProviderCapacityRequest(
                "provider-b",
                "example.com",
                "gmail.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "ACTIVE");

        assertThatThrownBy(() -> service.upsert("tenant-1", "workspace-a", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerId");
        verify(capacityProfileRepository, never()).save(any());
    }

    @Test
    void list_usesPageRequestWithRequestedLimit() {
        when(capacityProfileRepository.findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(
                any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        service.list("tenant-1", "workspace-a", 25);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(capacityProfileRepository).findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(
                eq("tenant-1"),
                eq("workspace-a"),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
    }

    @Test
    void list_clampsInvalidLimitToDefault() {
        when(capacityProfileRepository.findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(
                any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        service.list("tenant-1", "workspace-a", 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(capacityProfileRepository).findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(
                eq("tenant-1"),
                eq("workspace-a"),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void list_clampsExcessiveLimitToMax() {
        when(capacityProfileRepository.findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(
                any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        service.list("tenant-1", "workspace-a", 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(capacityProfileRepository).findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(
                eq("tenant-1"),
                eq("workspace-a"),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void upsert_rejectsFailoverProviderOutsideWorkspace() {
        when(smtpProviderRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                "provider-a", "tenant-1", "workspace-a"))
                .thenReturn(Optional.of(provider("provider-a", "workspace-a")));
        when(smtpProviderRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                "provider-b", "tenant-1", "workspace-a"))
                .thenReturn(Optional.empty());

        ProviderCapacityService.ProviderCapacityRequest request = new ProviderCapacityService.ProviderCapacityRequest(
                "provider-a",
                "example.com",
                "gmail.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "provider-b",
                "ACTIVE");

        assertThatThrownBy(() -> service.upsert("tenant-1", "workspace-a", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failoverProviderId");
        verify(capacityProfileRepository, never()).save(any());
    }

    @Test
    void evaluate_rejectsProviderOutsideWorkspace() {
        when(smtpProviderRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                "provider-b", "tenant-1", "workspace-a"))
                .thenReturn(Optional.empty());

        ProviderCapacityService.ThrottleRequest request = new ProviderCapacityService.ThrottleRequest(
                "provider-b",
                "example.com",
                "gmail.com",
                0);

        assertThatThrownBy(() -> service.evaluate("tenant-1", "workspace-a", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerId");
        verify(capacityProfileRepository, never()).save(any());
    }

    @Test
    void evaluate_rejectsStoredFailoverProviderOutsideWorkspace() {
        ProviderCapacityProfile profile = new ProviderCapacityProfile();
        profile.setProviderId("provider-a");
        profile.setSenderDomain("example.com");
        profile.setIspDomain("gmail.com");
        profile.setHourlyCap(1000);
        profile.setCurrentMaxPerMinute(60);
        profile.setObservedSuccessRate(1.0);
        profile.setMinSuccessRate(0.95);
        profile.setBounceRate(0.0);
        profile.setComplaintRate(0.0);
        profile.setBackpressureScore(0);
        profile.setStatus("ACTIVE");
        profile.setFailoverProviderId("provider-b");

        when(smtpProviderRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                "provider-a", "tenant-1", "workspace-a"))
                .thenReturn(Optional.of(provider("provider-a", "workspace-a")));
        when(capacityProfileRepository.findByTenantIdAndWorkspaceIdAndProviderIdAndSenderDomainAndIspDomain(
                "tenant-1", "workspace-a", "provider-a", "example.com", "gmail.com"))
                .thenReturn(Optional.of(profile));
        when(smtpProviderRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                "provider-b", "tenant-1", "workspace-a"))
                .thenReturn(Optional.empty());

        ProviderCapacityService.ThrottleRequest request = new ProviderCapacityService.ThrottleRequest(
                "provider-a",
                "example.com",
                "gmail.com",
                0);

        assertThatThrownBy(() -> service.evaluate("tenant-1", "workspace-a", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failoverProviderId");
        verify(capacityProfileRepository, never()).save(any());
    }

    @Test
    void runFailoverTest_rejectsFailoverProviderOutsideWorkspace() {
        when(smtpProviderRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                "provider-a", "tenant-1", "workspace-a"))
                .thenReturn(Optional.of(provider("provider-a", "workspace-a")));
        when(smtpProviderRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                "provider-b", "tenant-1", "workspace-a"))
                .thenReturn(Optional.empty());

        ProviderCapacityService.FailoverTestRequest request = new ProviderCapacityService.FailoverTestRequest(
                "provider-a",
                "provider-b");

        assertThatThrownBy(() -> service.runFailoverTest("tenant-1", "workspace-a", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failoverProviderId");
        verify(failoverTestRepository, never()).save(any());
    }

    private SmtpProvider provider(String id, String workspaceId) {
        SmtpProvider provider = new SmtpProvider();
        provider.setId(id);
        provider.setTenantId("tenant-1");
        provider.setWorkspaceId(workspaceId);
        return provider;
    }
}

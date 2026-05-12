package com.legent.delivery.service;

import com.legent.delivery.domain.ProviderCapacityProfile;
import com.legent.delivery.domain.ProviderHealthStatus;
import com.legent.delivery.repository.MessageLogRepository;
import com.legent.delivery.repository.ProviderCapacityProfileRepository;
import com.legent.delivery.repository.ProviderFailoverTestRepository;
import com.legent.delivery.repository.ProviderHealthStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ProviderCapacityServiceTest {

    @Mock private ProviderCapacityProfileRepository capacityProfileRepository;
    @Mock private ProviderHealthStatusRepository providerHealthStatusRepository;
    @Mock private ProviderFailoverTestRepository failoverTestRepository;
    @Mock private MessageLogRepository messageLogRepository;
    @Mock private DeliveryOperationsService deliveryOperationsService;

    private ProviderCapacityService service;

    @BeforeEach
    void setUp() {
        service = new ProviderCapacityService(
                capacityProfileRepository,
                providerHealthStatusRepository,
                failoverTestRepository,
                messageLogRepository,
                deliveryOperationsService);
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
}

package com.legent.foundation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import com.legent.common.event.UserSignedUpEvent;
import com.legent.foundation.domain.Tenant;
import com.legent.foundation.repository.TenantRepository;
import com.legent.foundation.service.TenantBootstrapService;
import com.legent.kafka.model.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningConsumerTest {

    private static final String TENANT_ID = "tenant-1";

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantBootstrapService tenantBootstrapService;

    private TenantProvisioningConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TenantProvisioningConsumer(
                tenantRepository,
                new ObjectMapper(),
                tenantBootstrapService);
    }

    @Test
    void rethrowsBootstrapFailureSoKafkaCanRetryProvisioning() {
        RuntimeException failure = new RuntimeException("bootstrap publish failed");
        when(tenantRepository.existsById(TENANT_ID)).thenReturn(false);
        when(tenantRepository.existsBySlug("acme")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(failure).when(tenantBootstrapService)
                .requestBootstrap(eq(TENANT_ID), eq("Acme"), eq("acme"), eq(false));

        assertThatThrownBy(() -> consumer.handleUserSignedUp(signupEnvelope()))
                .isSameAs(failure);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().getId()).isEqualTo(TENANT_ID);
        assertThat(tenantCaptor.getValue().getSlug()).isEqualTo("acme");
        verify(tenantBootstrapService).requestBootstrap(TENANT_ID, "Acme", "acme", false);
    }

    @Test
    void duplicateTenantStillSkipsProvisioningAsIdempotentReplay() {
        when(tenantRepository.existsById(TENANT_ID)).thenReturn(true);

        consumer.handleUserSignedUp(signupEnvelope());

        verify(tenantRepository, never()).save(any(Tenant.class));
        verifyNoInteractions(tenantBootstrapService);
    }

    private EventEnvelope<UserSignedUpEvent> signupEnvelope() {
        return EventEnvelope.<UserSignedUpEvent>builder()
                .eventId("event-1")
                .eventType(AppConstants.TOPIC_IDENTITY_USER_SIGNUP)
                .tenantId(TENANT_ID)
                .payload(UserSignedUpEvent.builder()
                        .companyName("Acme")
                        .slug("acme")
                        .build())
                .build();
    }
}

package com.legent.foundation.service;

import com.legent.common.constant.AppConstants;
import com.legent.foundation.domain.TenantBootstrapStatus;
import com.legent.foundation.repository.TenantBootstrapStatusRepository;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantBootstrapServiceTest {

    private static final String TENANT_ID = "tenant-1";

    @Mock private TenantBootstrapStatusRepository bootstrapStatusRepository;
    @Mock private CorePlatformService corePlatformService;
    @Mock private ConfigService configService;
    @Mock private EventPublisher eventPublisher;

    private TenantBootstrapService service;

    @BeforeEach
    void setUp() {
        service = new TenantBootstrapService(
                bootstrapStatusRepository,
                corePlatformService,
                configService,
                eventPublisher);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void requestBootstrapPropagatesAsyncRequestedPublishFailure() {
        RuntimeException failure = new RuntimeException("bootstrap requested publish failed");
        when(bootstrapStatusRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(bootstrapStatusRepository.save(any(TenantBootstrapStatus.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(eventPublisher.publish(
                eq(AppConstants.TOPIC_TENANT_BOOTSTRAP_REQUESTED),
                org.mockito.ArgumentMatchers.<EventEnvelope<Map<String, Object>>>any()))
                .thenReturn(CompletableFuture.failedFuture(failure));

        assertThatThrownBy(() -> service.requestBootstrap(TENANT_ID, "Acme", "acme", false))
                .isSameAs(failure);

        ArgumentCaptor<EventEnvelope<Map<String, Object>>> envelopeCaptor = envelopeCaptor();
        verify(eventPublisher).publish(eq(AppConstants.TOPIC_TENANT_BOOTSTRAP_REQUESTED), envelopeCaptor.capture());
        assertThat(envelopeCaptor.getValue().getPayload())
                .containsEntry("tenantId", TENANT_ID)
                .containsEntry("organizationName", "Acme")
                .containsEntry("organizationSlug", "acme")
                .containsEntry("force", false);
    }

    @Test
    void bootstrapTenantPropagatesAsyncCompletedPublishFailure() {
        RuntimeException failure = new RuntimeException("bootstrap completed publish failed");
        List<String> savedStatuses = new ArrayList<>();
        when(bootstrapStatusRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(bootstrapStatusRepository.save(any(TenantBootstrapStatus.class)))
                .thenAnswer(invocation -> {
                    TenantBootstrapStatus status = invocation.getArgument(0);
                    savedStatuses.add(status.getStatus());
                    return status;
                });
        stubExistingBootstrapDependencies();
        when(eventPublisher.publish(
                eq(AppConstants.TOPIC_TENANT_BOOTSTRAP_COMPLETED),
                org.mockito.ArgumentMatchers.<EventEnvelope<Map<String, Object>>>any()))
                .thenReturn(CompletableFuture.failedFuture(failure));

        assertThatThrownBy(() -> service.bootstrapTenant(TENANT_ID, "Acme", "acme", false))
                .isSameAs(failure);

        ArgumentCaptor<EventEnvelope<Map<String, Object>>> envelopeCaptor = envelopeCaptor();
        verify(eventPublisher).publish(eq(AppConstants.TOPIC_TENANT_BOOTSTRAP_COMPLETED), envelopeCaptor.capture());
        assertThat(envelopeCaptor.getValue().getPayload())
                .containsEntry("tenantId", TENANT_ID)
                .containsEntry("workspaceId", "workspace-1")
                .containsEntry("environmentId", "environment-1")
                .containsEntry("status", "COMPLETED");
        assertThat(savedStatuses).contains("COMPLETED", "FAILED");
    }

    private void stubExistingBootstrapDependencies() {
        when(corePlatformService.listOrganizations()).thenReturn(List.of(Map.of("id", "organization-1")));
        when(corePlatformService.listWorkspaces()).thenReturn(List.of(Map.of(
                "id", "workspace-1",
                "slug", "default")));
        when(corePlatformService.listTeams()).thenReturn(List.of(Map.of(
                "id", "team-1",
                "workspace_id", "workspace-1",
                "name", "Default Team")));
        when(corePlatformService.listEnvironments()).thenReturn(List.of(Map.of(
                "id", "environment-1",
                "workspace_id", "workspace-1",
                "environment_key", "PRODUCTION")));
        when(corePlatformService.listQuotaPolicies()).thenReturn(List.of(Map.of("metric_key", "contacts")));
        when(corePlatformService.listFeatureControls()).thenReturn(List.of(Map.of("feature_key", "core.admin")));
        when(corePlatformService.listSubscriptions()).thenReturn(List.of(Map.of("plan_key", "STARTER")));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<EventEnvelope<Map<String, Object>>> envelopeCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(EventEnvelope.class);
    }
}

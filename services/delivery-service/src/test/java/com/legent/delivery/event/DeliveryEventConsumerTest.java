package com.legent.delivery.event;

import com.legent.common.constant.AppConstants;
import com.legent.delivery.service.DeliveryEventIdempotencyService;
import com.legent.delivery.service.DeliveryOrchestrationService;
import com.legent.kafka.model.EventEnvelope;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryEventConsumerTest {

    @Mock private DeliveryOrchestrationService orchestrationService;
    @Mock private DeliveryEventIdempotencyService idempotencyService;

    private DeliveryEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DeliveryEventConsumer(orchestrationService, idempotencyService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void handleSendRequest_releasesClaimOnFailureAndRetryProcesses() {
        Map<String, Object> payload = Map.of(
                "workspaceId", "workspace-1",
                "email", "recipient@example.com");
        EventEnvelope<Map<String, Object>> event = EventEnvelope.<Map<String, Object>>builder()
                .eventId("evt-1")
                .eventType(AppConstants.TOPIC_EMAIL_SEND_REQUESTED)
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .environmentId("production")
                .actorId("user-1")
                .idempotencyKey("idem-1")
                .payload(payload)
                .build();
        RuntimeException failure = new IllegalStateException("database unavailable");
        AtomicInteger attempts = new AtomicInteger();

        when(idempotencyService.claimIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
                "evt-1",
                "idem-1")).thenReturn(true, true);
        doAnswer(invocation -> {
            assertThat(TenantContext.getTenantId()).isEqualTo("tenant-1");
            assertThat(TenantContext.getWorkspaceId()).isEqualTo("workspace-1");
            assertThat(TenantContext.getEnvironmentId()).isEqualTo("production");
            assertThat(TenantContext.getUserId()).isEqualTo("user-1");
            assertThat(TenantContext.getRequestId()).isEqualTo("idem-1");
            if (attempts.incrementAndGet() == 1) {
                throw failure;
            }
            return null;
        }).when(orchestrationService).processSendRequest(anyMap(), eq("tenant-1"), eq("evt-1"));

        assertThatThrownBy(() -> consumer.handleSendRequest(event))
                .isSameAs(failure);
        consumer.handleSendRequest(event);

        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getWorkspaceId()).isNull();
        assertThat(TenantContext.getEnvironmentId()).isNull();
        assertThat(TenantContext.getUserId()).isNull();
        assertThat(TenantContext.getRequestId()).isNull();
        verify(orchestrationService, org.mockito.Mockito.times(2)).processSendRequest(payload, "tenant-1", "evt-1");
        verify(idempotencyService).releaseClaim(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
                "evt-1",
                "idem-1");
        verify(idempotencyService).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
                "evt-1",
                "idem-1");
    }

    @Test
    void handleSendRequest_rejectsEnvelopePayloadWorkspaceMismatch() {
        EventEnvelope<Map<String, Object>> event = EventEnvelope.<Map<String, Object>>builder()
                .eventId("evt-2")
                .eventType(AppConstants.TOPIC_EMAIL_SEND_REQUESTED)
                .tenantId("tenant-1")
                .workspaceId("workspace-a")
                .idempotencyKey("idem-2")
                .payload(Map.of("workspaceId", "workspace-b", "email", "recipient@example.com"))
                .build();

        assertThatThrownBy(() -> consumer.handleSendRequest(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId mismatch");

        verifyNoInteractions(idempotencyService, orchestrationService);
    }
}

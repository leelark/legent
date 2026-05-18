package com.legent.delivery.event;

import com.legent.common.constant.AppConstants;
import com.legent.delivery.service.DeliveryEventIdempotencyService;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailFailedConsumerTest {

    @Mock private EventPublisher eventPublisher;
    @Mock private DeliveryEventIdempotencyService idempotencyService;

    private EmailFailedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new EmailFailedConsumer(eventPublisher, idempotencyService);
    }

    @Test
    void consumeEmailFailed_missingWorkspaceThrowsWithoutClaimOrReleaseSideEffects() {
        EventEnvelope<Map<String, Object>> envelope = failedEvent()
                .workspaceId(null)
                .payload(Map.of("messageId", "message-1"))
                .build();

        assertThatThrownBy(() -> consumer.consumeEmailFailed(envelope))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid failed-email event")
                .cause()
                .hasMessageContaining("Missing workspaceId");

        verifyNoInteractions(idempotencyService, eventPublisher);
    }

    @Test
    void consumeEmailFailed_missingTenantThrowsWithoutSideEffects() {
        EventEnvelope<Map<String, Object>> envelope = failedEvent()
                .tenantId(null)
                .build();

        assertThatThrownBy(() -> consumer.consumeEmailFailed(envelope))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid failed-email event")
                .cause()
                .hasMessageContaining("Missing tenantId");

        verifyNoInteractions(idempotencyService, eventPublisher);
    }

    @Test
    void consumeEmailFailed_missingEventIdentityThrowsWithoutSideEffects() {
        EventEnvelope<Map<String, Object>> envelope = failedEvent()
                .eventId(" ")
                .idempotencyKey(null)
                .build();

        assertThatThrownBy(() -> consumer.consumeEmailFailed(envelope))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid failed-email event")
                .cause()
                .hasMessageContaining("Missing eventId or idempotencyKey");

        verifyNoInteractions(idempotencyService, eventPublisher);
    }

    @Test
    void consumeEmailFailed_duplicateClaimSkipsPublishAndRelease() {
        EventEnvelope<Map<String, Object>> envelope = failedEvent().build();
        when(idempotencyService.claimIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_FAILED,
                "evt-1",
                "idem-1")).thenReturn(false);

        consumer.consumeEmailFailed(envelope);

        verify(idempotencyService).claimIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_FAILED,
                "evt-1",
                "idem-1");
        verifyNoInteractions(eventPublisher);
        verify(idempotencyService, never()).markProcessed(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(idempotencyService, never()).releaseClaim(anyString(), anyString(), anyString(), anyString(), anyString());
        verifyNoMoreInteractions(idempotencyService);
    }

    @Test
    void consumeEmailFailed_schedulesRetryAndMarksProcessedAfterClaim() {
        EventEnvelope<Map<String, Object>> envelope = failedEvent().build();
        when(idempotencyService.claimIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_FAILED,
                "evt-1",
                "idem-1")).thenReturn(true);
        when(eventPublisher.publish(
                eq(AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED),
                eq("tenant-1"),
                org.mockito.ArgumentMatchers.<EventEnvelope<Map<String, Object>>>any()))
                .thenReturn(completedPublish());

        consumer.consumeEmailFailed(envelope);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<Map<String, Object>>> retryCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventPublisher).publish(eq(AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED), eq("tenant-1"), retryCaptor.capture());
        assertThat(retryCaptor.getValue().getRetryCount()).isEqualTo(1);
        verify(idempotencyService).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_FAILED,
                "evt-1",
                "idem-1");
    }

    @Test
    void consumeEmailFailed_releasesClaimWhenPublishFails() {
        EventEnvelope<Map<String, Object>> envelope = failedEvent().build();
        CompletableFuture<SendResult<String, Object>> publishFailure = new CompletableFuture<>();
        publishFailure.completeExceptionally(new IllegalStateException("kafka unavailable"));
        when(idempotencyService.claimIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_FAILED,
                "evt-1",
                "idem-1")).thenReturn(true);
        when(eventPublisher.publish(
                eq(AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED),
                eq("tenant-1"),
                org.mockito.ArgumentMatchers.<EventEnvelope<Map<String, Object>>>any()))
                .thenReturn(publishFailure);

        assertThatThrownBy(() -> consumer.consumeEmailFailed(envelope))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to process failed email event evt-1");

        verify(idempotencyService).releaseClaim(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_FAILED,
                "evt-1",
                "idem-1");
        verify(idempotencyService, never()).markProcessed(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private EventEnvelope.EventEnvelopeBuilder<Map<String, Object>> failedEvent() {
        return EventEnvelope.<Map<String, Object>>builder()
                .eventId("evt-1")
                .eventType(AppConstants.TOPIC_EMAIL_FAILED)
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .idempotencyKey("idem-1")
                .retryCount(0)
                .payload(Map.of(
                        "workspaceId", "workspace-1",
                        "messageId", "message-1"));
    }

    private CompletableFuture<SendResult<String, Object>> completedPublish() {
        return CompletableFuture.completedFuture(null);
    }
}

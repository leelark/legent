package com.legent.delivery.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import com.legent.delivery.domain.SuppressionSignal;
import com.legent.delivery.service.DeliveryEventIdempotencyService;
import com.legent.delivery.service.SuppressionSignalService;
import com.legent.kafka.model.EventEnvelope;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliverySuppressionSignalConsumerTest {

    @Mock private SuppressionSignalService suppressionSignalService;
    @Mock private DeliveryEventIdempotencyService idempotencyService;

    private DeliverySuppressionSignalConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DeliverySuppressionSignalConsumer(new ObjectMapper(), suppressionSignalService, idempotencyService);
    }

    @Test
    void bouncedHardBounceRecordsSuppressionSignalAndMarksProcessed() {
        EventEnvelope<Map<String, Object>> envelope = event(AppConstants.TOPIC_EMAIL_BOUNCED, Map.of(
                "workspaceId", "workspace-1",
                "email", "User@Example.com",
                "type", "HARD_BOUNCE",
                "reason", "550 hard bounce",
                "messageId", "message-1"));
        when(idempotencyService.claimIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_BOUNCED,
                "event-1",
                "idem-1"))
                .thenReturn(true);

        consumer.consumeSuppressionSignal(envelope, AppConstants.TOPIC_EMAIL_BOUNCED);

        verify(suppressionSignalService).recordSignal(
                "tenant-1",
                "workspace-1",
                "User@Example.com",
                SuppressionSignal.SignalType.HARD_BOUNCE,
                "550 hard bounce",
                "message-1");
        verify(idempotencyService).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_BOUNCED,
                "event-1",
                "idem-1");
    }

    @Test
    void complaintRecordsComplaintSignal() {
        EventEnvelope<Map<String, Object>> envelope = event(AppConstants.TOPIC_EMAIL_COMPLAINT, Map.of(
                "workspaceId", "workspace-1",
                "email", "complaint@example.com",
                "reason", "fbl",
                "messageId", "message-2"));
        when(idempotencyService.claimIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_COMPLAINT,
                "event-1",
                "idem-1"))
                .thenReturn(true);

        consumer.consumeSuppressionSignal(envelope, AppConstants.TOPIC_EMAIL_COMPLAINT);

        verify(suppressionSignalService).recordSignal(
                "tenant-1",
                "workspace-1",
                "complaint@example.com",
                SuppressionSignal.SignalType.COMPLAINT,
                "fbl",
                "message-2");
    }

    @Test
    void unsubscribeRecordsUnsubscribeSignal() {
        EventEnvelope<Map<String, Object>> envelope = event(AppConstants.TOPIC_EMAIL_UNSUBSCRIBED, Map.of(
                "workspaceId", "workspace-1",
                "email", "optout@example.com",
                "messageId", "message-3"));
        when(idempotencyService.claimIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_UNSUBSCRIBED,
                "event-1",
                "idem-1"))
                .thenReturn(true);

        consumer.consumeSuppressionSignal(envelope, AppConstants.TOPIC_EMAIL_UNSUBSCRIBED);

        verify(suppressionSignalService).recordSignal(
                "tenant-1",
                "workspace-1",
                "optout@example.com",
                SuppressionSignal.SignalType.UNSUBSCRIBE,
                "UNSUBSCRIBE",
                "message-3");
    }

    @Test
    void duplicateClaimSkipsWriteAndMarkProcessed() {
        EventEnvelope<Map<String, Object>> envelope = event(AppConstants.TOPIC_EMAIL_COMPLAINT, Map.of(
                "workspaceId", "workspace-1",
                "email", "complaint@example.com"));
        when(idempotencyService.claimIfNew(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_COMPLAINT,
                "event-1",
                "idem-1"))
                .thenReturn(false);

        consumer.consumeSuppressionSignal(envelope, AppConstants.TOPIC_EMAIL_COMPLAINT);

        verifyNoInteractions(suppressionSignalService);
        verify(idempotencyService, never()).markProcessed(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_EMAIL_COMPLAINT,
                "event-1",
                "idem-1");
    }

    @Test
    void missingEmailFailsBeforeClaim() {
        EventEnvelope<Map<String, Object>> envelope = event(AppConstants.TOPIC_EMAIL_COMPLAINT, Map.of(
                "workspaceId", "workspace-1"));

        assertThatThrownBy(() -> consumer.consumeSuppressionSignal(envelope, AppConstants.TOPIC_EMAIL_COMPLAINT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid delivery suppression signal event")
                .cause()
                .hasMessageContaining("email is required");

        verifyNoInteractions(idempotencyService, suppressionSignalService);
    }

    private EventEnvelope<Map<String, Object>> event(String eventType, Map<String, Object> payload) {
        return EventEnvelope.<Map<String, Object>>builder()
                .eventId("event-1")
                .eventType(eventType)
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .idempotencyKey("idem-1")
                .payload(payload)
                .build();
    }
}

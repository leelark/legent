package com.legent.audience.event;

import com.legent.audience.service.SubscriberIntelligenceService;
import com.legent.kafka.model.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AudienceIntelligenceConsumerTest {

    @Mock
    private SubscriberIntelligenceService intelligenceService;

    @InjectMocks
    private AudienceIntelligenceConsumer consumer;

    @Test
    void consumeTracking_MissingWorkspace_ThrowsForRetryBeforeSideEffects() {
        EventEnvelope<String> envelope = new EventEnvelope<>();
        envelope.setTenantId("tenant-1");
        envelope.setEventId("evt-1");
        envelope.setIdempotencyKey("idem-1");
        envelope.setPayload("{}");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> consumer.consumeTracking(envelope));

        assertThat(thrown.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing workspaceId")
                .hasMessageContaining("eventId=evt-1");
        verifyNoInteractions(intelligenceService);
    }

    @Test
    void consumeAutomation_MissingIdempotencyKey_ThrowsForRetryBeforeSideEffects() {
        EventEnvelope<String> envelope = new EventEnvelope<>();
        envelope.setTenantId("tenant-1");
        envelope.setWorkspaceId("workspace-1");
        envelope.setEventType("workflow.step.completed");
        envelope.setEventId("evt-1");
        envelope.setPayload("{}");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> consumer.consumeAutomation(envelope));

        assertThat(thrown.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing idempotencyKey")
                .hasMessageContaining("eventId=evt-1");
        verifyNoInteractions(intelligenceService);
    }

    @Test
    void consumeTracking_ServiceFailure_RethrowsForRetry() {
        EventEnvelope<String> envelope = new EventEnvelope<>();
        envelope.setTenantId("tenant-1");
        envelope.setWorkspaceId("workspace-1");
        envelope.setEventType("tracking.ingested");
        envelope.setEventId("evt-1");
        envelope.setIdempotencyKey("idem-1");
        envelope.setPayload("{}");

        doThrow(new RuntimeException("database unavailable")).when(intelligenceService)
                .applyTrackingIngested("tenant-1", "workspace-1", "tracking.ingested", "evt-1", "idem-1", "{}");

        assertThrows(IllegalStateException.class, () -> consumer.consumeTracking(envelope));
    }
}

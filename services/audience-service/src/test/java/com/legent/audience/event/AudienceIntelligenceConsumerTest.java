package com.legent.audience.event;

import com.legent.audience.service.SubscriberIntelligenceService;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AudienceIntelligenceConsumerTest {

    @Mock
    private SubscriberIntelligenceService intelligenceService;

    @InjectMocks
    private AudienceIntelligenceConsumer consumer;

    private EventEnvelope<String> validTrackingEnvelope;

    @BeforeEach
    void setUp() {
        validTrackingEnvelope = trackingEnvelope("evt-1", "idem-1", AppConstants.TOPIC_TRACKING_INGESTED);
    }

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
        doThrow(new RuntimeException("database unavailable")).when(intelligenceService)
                .applyTrackingIngested("tenant-1", "workspace-1", "tracking.ingested", "evt-1", "idem-1", "{}");

        assertThrows(IllegalStateException.class, () -> consumer.consumeTracking(validTrackingEnvelope));
    }

    @Test
    void consumeTrackingBatch_MixedInvalidUnsupportedAndValid_ProcessesOnlyValidEvents() {
        EventEnvelope<String> missingWorkspace = trackingEnvelope("evt-invalid", "idem-invalid", AppConstants.TOPIC_TRACKING_INGESTED);
        missingWorkspace.setWorkspaceId(null);
        EventEnvelope<String> unsupported = trackingEnvelope("evt-unsupported", "idem-unsupported", "email.open");

        EventEnvelope<String> duplicate = trackingEnvelope("evt-1", "idem-1", AppConstants.TOPIC_TRACKING_INGESTED);

        consumer.consumeTrackingBatch(List.of(missingWorkspace, unsupported, validTrackingEnvelope, duplicate));

        verify(intelligenceService).applyTrackingIngested(
                "tenant-1",
                "workspace-1",
                AppConstants.TOPIC_TRACKING_INGESTED,
                "evt-1",
                "idem-1",
                "{}");
        verifyNoMoreInteractions(intelligenceService);
    }

    @Test
    void consumeTrackingBatch_ServiceFailure_RethrowsForRetry() {
        doThrow(new RuntimeException("database unavailable")).when(intelligenceService)
                .applyTrackingIngested("tenant-1", "workspace-1", "tracking.ingested", "evt-1", "idem-1", "{}");

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> consumer.consumeTrackingBatch(List.of(validTrackingEnvelope)));

        assertThat(thrown)
                .hasMessageContaining("Failed to process audience tracking intelligence event batch")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void consumeTrackingBatch_EmptyBatch_NoOps() {
        consumer.consumeTrackingBatch(List.of());

        verifyNoInteractions(intelligenceService);
    }

    @Test
    void trackingKafkaListenerUsesBatchContainerFactory() throws NoSuchMethodException {
        Method batchListener = AudienceIntelligenceConsumer.class.getDeclaredMethod("consumeTrackingBatch", List.class);
        KafkaListener annotation = batchListener.getAnnotation(KafkaListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.topics()).containsExactly(AppConstants.TOPIC_TRACKING_INGESTED);
        assertThat(annotation.groupId()).isEqualTo("audience-tracking-group");
        assertThat(annotation.containerFactory()).isEqualTo("audienceTrackingIngestedKafkaListenerContainerFactory");
        assertThat(AudienceIntelligenceConsumer.class
                .getDeclaredMethod("consumeTracking", EventEnvelope.class)
                .getAnnotation(KafkaListener.class)).isNull();
    }

    private EventEnvelope<String> trackingEnvelope(String eventId, String idempotencyKey, String eventType) {
        EventEnvelope<String> envelope = new EventEnvelope<>();
        envelope.setTenantId("tenant-1");
        envelope.setWorkspaceId("workspace-1");
        envelope.setEventType(eventType);
        envelope.setEventId(eventId);
        envelope.setIdempotencyKey(idempotencyKey);
        envelope.setPayload("{}");
        return envelope;
    }
}

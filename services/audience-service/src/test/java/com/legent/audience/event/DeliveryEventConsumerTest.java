package com.legent.audience.event;

import com.legent.audience.repository.SubscriberRepository;
import com.legent.audience.repository.SuppressionRepository;
import com.legent.audience.service.AudienceEventIdempotencyService;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DeliveryEventConsumerTest {

    @Mock
    private SubscriberRepository subscriberRepository;

    @Mock
    private SuppressionRepository suppressionRepository;

    @Mock
    private AudienceEventIdempotencyService idempotencyService;

    @InjectMocks
    private DeliveryEventConsumer consumer;

    @Test
    void handleDeliveryEvent_MissingWorkspace_ThrowsBeforeSideEffects() {
        EventEnvelope<Map<String, Object>> envelope = deliveryEnvelope(Map.of("email", "user@example.test"));
        envelope.setWorkspaceId(" ");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> consumer.handleDeliveryEvent(envelope));

        assertThat(thrown.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing workspaceId")
                .hasMessageContaining("eventId=evt-1");
        verifyNoInteractions(idempotencyService, subscriberRepository, suppressionRepository);
    }

    @Test
    void handleDeliveryEvent_MissingEmail_ThrowsBeforeSideEffects() {
        EventEnvelope<Map<String, Object>> envelope = deliveryEnvelope(Map.of("reason", "hard bounce"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> consumer.handleDeliveryEvent(envelope));

        assertThat(thrown.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing email")
                .hasMessageContaining("eventId=evt-1");
        verifyNoInteractions(idempotencyService, subscriberRepository, suppressionRepository);
    }

    @Test
    void handleDeliveryEvent_MissingIdempotencyKey_ThrowsBeforeSideEffects() {
        EventEnvelope<Map<String, Object>> envelope = deliveryEnvelope(Map.of("email", "user@example.test"));
        envelope.setIdempotencyKey(null);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> consumer.handleDeliveryEvent(envelope));

        assertThat(thrown.getCause())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing idempotencyKey")
                .hasMessageContaining("eventId=evt-1");
        verifyNoInteractions(idempotencyService, subscriberRepository, suppressionRepository);
    }

    private EventEnvelope<Map<String, Object>> deliveryEnvelope(Map<String, Object> payload) {
        EventEnvelope<Map<String, Object>> envelope = new EventEnvelope<>();
        envelope.setTenantId("tenant-1");
        envelope.setWorkspaceId("workspace-1");
        envelope.setEventType(AppConstants.TOPIC_EMAIL_BOUNCED);
        envelope.setEventId("evt-1");
        envelope.setIdempotencyKey("idem-1");
        envelope.setPayload(payload);
        return envelope;
    }
}

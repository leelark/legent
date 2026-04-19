package com.legent.tracking.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;

import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.tracking.dto.TrackingDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrackingEventPublisher {

    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private static final String SOURCE = "tracking-service";

    public void publishIngestedEvent(TrackingDto.RawEventPayload payload) {
        try {
            // Publish to intermediate holding topic
            EventEnvelope<String> envelope = EventEnvelope.wrap(
                    AppConstants.TOPIC_TRACKING_INGESTED, 
                    payload.getTenantId(), 
                    SOURCE,
                    objectMapper.writeValueAsString(payload)
            );
            eventPublisher.publish(AppConstants.TOPIC_TRACKING_INGESTED, payload.getMessageId(), envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish tracking ingestion", e);
        }
    }
}

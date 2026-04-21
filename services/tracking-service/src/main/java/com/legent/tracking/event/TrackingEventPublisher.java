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

    public void publishOpen(String mid) {
        try {
            EventEnvelope<String> envelope = EventEnvelope.wrap(
                    AppConstants.TOPIC_EMAIL_OPEN,
                    null, // tenantId might need to be extracted from mid or passed
                    SOURCE,
                    mid
            );
            eventPublisher.publish(AppConstants.TOPIC_EMAIL_OPEN, mid, envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish email open event", e);
        }
    }

    public void publishClick(String mid, String url) {
        try {
            EventEnvelope<String> envelope = EventEnvelope.wrap(
                    AppConstants.TOPIC_EMAIL_CLICK,
                    null, // tenantId
                    SOURCE,
                    "{\"mid\":\"" + mid + "\",\"url\":\"" + url + "\"}"
            );
            eventPublisher.publish(AppConstants.TOPIC_EMAIL_CLICK, mid, envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish email click event", e);
        }
    }

    public void publishConversion(String mid, String payload) {
        try {
            EventEnvelope<String> envelope = EventEnvelope.wrap(
                    AppConstants.TOPIC_CONVERSION_EVENT,
                    null, // tenantId
                    SOURCE,
                    payload
            );
            eventPublisher.publish(AppConstants.TOPIC_CONVERSION_EVENT, mid, envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish conversion event", e);
        }
    }
}

package com.legent.tracking.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;

import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.tracking.dto.TrackingDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingEventPublisher {

    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private static final String SOURCE = "tracking-service";

    public void publishIngestedEvent(TrackingDto.RawEventPayload payload) {
        try {
            publishIngestedEventOrThrow(payload);
        } catch (Exception e) {
            log.error("Failed to publish tracking ingestion for mid={}", payload.getMessageId(), e);
        }
    }

    public void publishIngestedEventOrThrow(TrackingDto.RawEventPayload payload) throws Exception {
        EventEnvelope<String> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_TRACKING_INGESTED,
                payload.getTenantId(),
                SOURCE,
                objectMapper.writeValueAsString(payload)
        );
        envelope.setWorkspaceId(payload.getWorkspaceId());
        envelope.setEnvironmentId(payload.getEnvironmentId());
        envelope.setActorId(payload.getActorId());
        envelope.setOwnershipScope(payload.getOwnershipScope() == null ? "WORKSPACE" : payload.getOwnershipScope());
        String idempotencyKey = firstNonBlank(payload.getIdempotencyKey(), payload.getId());
        if (idempotencyKey != null) {
            envelope.setIdempotencyKey(idempotencyKey);
        }
        CompletableFuture<?> send = eventPublisher.publish(AppConstants.TOPIC_TRACKING_INGESTED, payload.getMessageId(), envelope);
        if (send == null) {
            throw new IllegalStateException("Kafka publish returned no send future");
        }
        send.get();
    }

    public void publishOpen(String mid, String tenantId) {
        try {
            EventEnvelope<String> envelope = EventEnvelope.wrap(
                    AppConstants.TOPIC_EMAIL_OPEN,
                    tenantId,
                    SOURCE,
                    mid
            );
            eventPublisher.publish(AppConstants.TOPIC_EMAIL_OPEN, mid, envelope);
        } catch (Exception e) {
            log.error("Failed to publish email open event for mid={}", mid, e);
        }
    }

    public void publishClick(String mid, String url, String tenantId) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of("mid", mid, "url", url));
            EventEnvelope<String> envelope = EventEnvelope.wrap(
                    AppConstants.TOPIC_EMAIL_CLICK,
                    tenantId,
                    SOURCE,
                    payload
            );
            eventPublisher.publish(AppConstants.TOPIC_EMAIL_CLICK, mid, envelope);
        } catch (Exception e) {
            log.error("Failed to publish email click event for mid={}", mid, e);
        }
    }

    public void publishConversion(String mid, String payload, String tenantId) {
        try {
            EventEnvelope<String> envelope = EventEnvelope.wrap(
                    AppConstants.TOPIC_CONVERSION_EVENT,
                    tenantId,
                    SOURCE,
                    payload
            );
            eventPublisher.publish(AppConstants.TOPIC_CONVERSION_EVENT, mid, envelope);
        } catch (Exception e) {
            log.error("Failed to publish conversion event for mid={}", mid, e);
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }
}

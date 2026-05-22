package com.legent.tracking.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;

import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.tracking.dto.TrackingDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class TrackingEventPublisher {

    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private static final String SOURCE = "tracking-service";

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
        String partitionKey = firstNonBlank(
                payload.getMessageId(),
                payload.getWorkflowRunId(),
                payload.getSubscriberId(),
                payload.getCampaignId(),
                payload.getId());
        CompletableFuture<?> send = eventPublisher.publish(AppConstants.TOPIC_TRACKING_INGESTED, partitionKey, envelope);
        if (send == null) {
            throw new IllegalStateException("Kafka publish returned no send future");
        }
        send.get();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}

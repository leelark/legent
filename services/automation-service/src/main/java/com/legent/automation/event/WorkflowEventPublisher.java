package com.legent.automation.event;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.CompletionException;

import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEventPublisher {

    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private static final String SOURCE = "automation-service";

    public void publishAction(String topic, String tenantId, String jobId, Map<String, Object> payload) {
        try {
            EventEnvelope<String> envelope = EventEnvelope.wrap(
                    topic, tenantId, SOURCE, objectMapper.writeValueAsString(payload)
            );
            Object workspaceId = payload == null ? null : payload.get("workspaceId");
            if ((envelope.getWorkspaceId() == null || envelope.getWorkspaceId().isBlank())
                    && workspaceId != null
                    && !String.valueOf(workspaceId).isBlank()) {
                envelope.setWorkspaceId(String.valueOf(workspaceId).trim());
                envelope.setOwnershipScope("WORKSPACE");
            }
            applyOptionalEnvelopeMetadata(envelope, payload);
            eventPublisher.publish(topic, jobId, envelope).join();
        } catch (CompletionException e) {
            throw publishFailure(topic, e);
        } catch (Exception e) {
            log.error("Failed to publish workflow action to {}", topic, e);
            throw new IllegalStateException("Failed to publish workflow action to " + topic, e);
        }
    }

    private RuntimeException publishFailure(String topic, CompletionException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        log.error("Failed to publish workflow action to {}", topic, cause);
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Failed to publish workflow action to " + topic, cause);
    }

    private <T> void applyOptionalEnvelopeMetadata(EventEnvelope<T> envelope, Map<String, Object> payload) {
        if (payload == null) {
            return;
        }
        Object idempotencyKey = payload.get("idempotencyKey");
        if (idempotencyKey != null && !String.valueOf(idempotencyKey).isBlank()) {
            envelope.setIdempotencyKey(String.valueOf(idempotencyKey).trim());
        }
        Object environmentId = payload.get("environmentId");
        if (environmentId != null && !String.valueOf(environmentId).isBlank()) {
            envelope.setEnvironmentId(String.valueOf(environmentId).trim());
        }
        Object actorId = payload.get("actorId");
        if (actorId != null && !String.valueOf(actorId).isBlank()) {
            envelope.setActorId(String.valueOf(actorId).trim());
        }
        Object ownershipScope = payload.get("ownershipScope");
        if (ownershipScope != null && !String.valueOf(ownershipScope).isBlank()) {
            envelope.setOwnershipScope(String.valueOf(ownershipScope).trim());
        }
    }
}

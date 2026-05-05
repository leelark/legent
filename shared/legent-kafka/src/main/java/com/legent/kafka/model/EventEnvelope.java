package com.legent.kafka.model;

import com.legent.common.util.IdGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Wrapper envelope for all Kafka events.
 * Adds metadata around the domain-specific payload.
 *
 * @param <T> the type of the event payload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope<T> {

    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String tenantId;
    private String workspaceId;
    private String environmentId;
    private String actorId;
    private String ownershipScope;
    private String correlationId;
    private String source;
    private int schemaVersion;
    private String idempotencyKey;
    private int retryCount;
    private T payload;

    /**
     * Factory method to create a new event envelope with generated IDs.
     */
    public static <T> EventEnvelope<T> wrap(
            String eventType,
            String tenantId,
            String source,
            T payload) {
        return EventEnvelope.<T>builder()
                .eventId(IdGenerator.newId())
                .eventType(eventType)
                .timestamp(Instant.now())
                .tenantId(tenantId)
                .workspaceId(null)
                .environmentId(null)
                .actorId(null)
                .ownershipScope("TENANT")
                .correlationId(IdGenerator.newCorrelationId())
                .source(source)
                .schemaVersion(1)
                .idempotencyKey(IdGenerator.newIdempotencyKey())
                .retryCount(0)
                .payload(payload)
                .build();
    }

    /**
     * Creates a copy for retry with incremented retry count.
     */
    public EventEnvelope<T> forRetry() {
        return EventEnvelope.<T>builder()
                .eventId(this.eventId)
                .eventType(this.eventType)
                .timestamp(Instant.now())
                .tenantId(this.tenantId)
                .workspaceId(this.workspaceId)
                .environmentId(this.environmentId)
                .actorId(this.actorId)
                .ownershipScope(this.ownershipScope)
                .correlationId(this.correlationId)
                .source(this.source)
                .schemaVersion(this.schemaVersion)
                .idempotencyKey(this.idempotencyKey)
                .retryCount(this.retryCount + 1)
                .payload(this.payload)
                .build();
    }
}

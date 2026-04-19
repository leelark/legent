package com.legent.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Base domain event interface.
 * All Kafka events in the platform extend this.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainEvent {

    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String tenantId;
    private String correlationId;
    private String source;
    private int version;
}

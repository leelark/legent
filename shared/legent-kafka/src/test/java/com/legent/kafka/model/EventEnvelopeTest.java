package com.legent.kafka.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EventEnvelopeTest {

    @Test
    void wrap_populatesStandardMetadata() {
        EventEnvelope<String> envelope = EventEnvelope.wrap(
                "campaign.created",
                "tenant-1",
                "campaign-service",
                "{\"id\":\"cmp-1\"}"
        );

        assertNotNull(envelope.getEventId());
        assertNotNull(envelope.getCorrelationId());
        assertNotNull(envelope.getIdempotencyKey());
        assertEquals("campaign.created", envelope.getEventType());
        assertEquals("tenant-1", envelope.getTenantId());
        assertEquals(0, envelope.getRetryCount());
    }

    @Test
    void forRetry_incrementsRetryCountAndRetainsCoreMetadata() {
        EventEnvelope<String> original = EventEnvelope.wrap(
                "campaign.created",
                "tenant-1",
                "campaign-service",
                "{\"id\":\"cmp-1\"}"
        );

        EventEnvelope<String> retried = original.forRetry();

        assertEquals(original.getEventId(), retried.getEventId());
        assertEquals(original.getCorrelationId(), retried.getCorrelationId());
        assertEquals(original.getIdempotencyKey(), retried.getIdempotencyKey());
        assertEquals(original.getRetryCount() + 1, retried.getRetryCount());
    }
}

package com.legent.tracking.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.tracking.dto.TrackingDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackingEventPublisherTest {

    @Test
    @SuppressWarnings("unchecked")
    void publishIngestedEventOrThrow_WhenKafkaAckFails_PropagatesFailure() {
        EventPublisher eventPublisher = mock(EventPublisher.class);
        TrackingEventPublisher publisher = new TrackingEventPublisher(
                eventPublisher,
                new ObjectMapper().findAndRegisterModules());
        RuntimeException failure = new RuntimeException("kafka ack failed");
        when(eventPublisher.publish(eq(AppConstants.TOPIC_TRACKING_INGESTED), eq("message-1"), any(EventEnvelope.class)))
                .thenReturn(CompletableFuture.failedFuture(failure));

        TrackingDto.RawEventPayload payload = TrackingDto.RawEventPayload.builder()
                .id("event-1")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .messageId("message-1")
                .eventType("OPEN")
                .timestamp(Instant.now())
                .build();

        ExecutionException thrown = assertThrows(
                ExecutionException.class,
                () -> publisher.publishIngestedEventOrThrow(payload));

        assertThat(thrown.getCause()).isSameAs(failure);
    }
}

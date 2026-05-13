package com.legent.kafka.service;

import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void publish_highVolumeTopicUsesPayloadRoutingMetadataBeforeTenant() {
        stubSend();
        EventPublisher publisher = new EventPublisher(kafkaTemplate);
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_EMAIL_SEND_REQUESTED,
                Map.of(
                        "tenantId", "tenant-1",
                        "jobId", "job-1",
                        "batchId", "batch-1",
                        "messageId", "message-1"
                )
        );

        publisher.publish(AppConstants.TOPIC_EMAIL_SEND_REQUESTED, envelope);

        verify(kafkaTemplate).send(eq(AppConstants.TOPIC_EMAIL_SEND_REQUESTED), eq("message-1"), eq(envelope));
    }

    @Test
    void publish_highVolumeTopicDoesNotFallBackToTenantId() {
        stubSend();
        EventPublisher publisher = new EventPublisher(kafkaTemplate);
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_EMAIL_SENT,
                Map.of("subject", "No routing metadata")
        );

        publisher.publish(AppConstants.TOPIC_EMAIL_SENT, envelope);

        verify(kafkaTemplate).send(eq(AppConstants.TOPIC_EMAIL_SENT), eq("event-1"), eq(envelope));
    }

    @Test
    void publish_highVolumeTopicRejectsMissingRoutingAndEventMetadata() {
        EventPublisher publisher = new EventPublisher(kafkaTemplate);
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_EMAIL_SENT,
                Map.of("subject", "No routing metadata")
        );
        envelope.setEventId(null);
        envelope.setIdempotencyKey(null);
        envelope.setCorrelationId(null);

        assertThrows(IllegalArgumentException.class,
                () -> publisher.publish(AppConstants.TOPIC_EMAIL_SENT, envelope));
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void publish_customTenantKeyForHighVolumeTopicUsesPayloadRoutingMetadata() {
        stubSend();
        EventPublisher publisher = new EventPublisher(kafkaTemplate);
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED,
                Map.of("messageId", "message-2")
        );

        publisher.publish(AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED, "tenant-1", envelope);

        verify(kafkaTemplate).send(eq(AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED), eq("message-2"), eq(envelope));
    }

    @Test
    void publish_lowVolumeTopicFallsBackToTenantId() {
        stubSend();
        EventPublisher publisher = new EventPublisher(kafkaTemplate);
        EventEnvelope<Map<String, Object>> envelope = envelope(
                AppConstants.TOPIC_CONFIG_UPDATED,
                Map.of("scope", "workspace")
        );

        publisher.publish(AppConstants.TOPIC_CONFIG_UPDATED, envelope);

        verify(kafkaTemplate).send(eq(AppConstants.TOPIC_CONFIG_UPDATED), eq("tenant-1"), eq(envelope));
    }

    private EventEnvelope<Map<String, Object>> envelope(String topic, Map<String, Object> payload) {
        return EventEnvelope.<Map<String, Object>>builder()
                .eventId("event-1")
                .eventType(topic)
                .tenantId("tenant-1")
                .payload(payload)
                .build();
    }

    private void stubSend() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedSend());
    }

    private CompletableFuture<SendResult<String, Object>> failedSend() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("test failure"));
        return future;
    }
}

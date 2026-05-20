package com.legent.delivery.event;

import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryEventPublisherTest {

    @Mock
    private EventPublisher eventPublisher;

    private DeliveryEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new DeliveryEventPublisher(eventPublisher);
    }

    @Test
    void publishEmailSent_WhenKafkaAckFails_PropagatesFailure() {
        CompletableFuture<SendResult<String, Object>> failedPublish = CompletableFuture.failedFuture(
                new IllegalStateException("kafka unavailable"));
        when(eventPublisher.publish(eq(AppConstants.TOPIC_EMAIL_SENT), anyString(), any(EventEnvelope.class)))
                .thenReturn(failedPublish);

        assertThrows(CompletionException.class, () -> publisher.publishEmailSent(
                "tenant-1",
                "workspace-1",
                "msg-1",
                "camp-1",
                "job-1",
                "batch-1",
                "sub-1",
                Map.of("riskScore", "7")));
    }

    @Test
    void publishEmailSent_BuildsTenantWorkspaceMessageEnvelope() {
        when(eventPublisher.publish(eq(AppConstants.TOPIC_EMAIL_SENT), anyString(), any(EventEnvelope.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        DeliveryFeedbackMessage message = publisher.emailSentMessage(
                "tenant-1",
                "workspace-1",
                "msg-1",
                "camp-1",
                "job-1",
                "batch-1",
                "sub-1",
                Map.of("riskScore", "7"));
        publisher.publishOrThrow(message);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<Map<String, String>>> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventPublisher).publish(eq(AppConstants.TOPIC_EMAIL_SENT), eq("msg-1"), envelopeCaptor.capture());
        EventEnvelope<Map<String, String>> envelope = envelopeCaptor.getValue();
        assertEquals(AppConstants.TOPIC_EMAIL_SENT, envelope.getEventType());
        assertEquals("tenant-1", envelope.getTenantId());
        assertEquals("workspace-1", envelope.getWorkspaceId());
        assertEquals("delivery-service", envelope.getSource());
        assertEquals(1, envelope.getSchemaVersion());
        assertEquals("msg-1", envelope.getPayload().get("messageId"));
        assertEquals("camp-1", envelope.getPayload().get("campaignId"));
        assertEquals("job-1", envelope.getPayload().get("jobId"));
        assertEquals("batch-1", envelope.getPayload().get("batchId"));
        assertEquals("sub-1", envelope.getPayload().get("subscriberId"));
        assertEquals("7", envelope.getPayload().get("riskScore"));
    }
}

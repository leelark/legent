package com.legent.campaign.event;

import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignEventPublisherTest {

    @Mock private EventPublisher eventPublisher;

    private CampaignEventPublisher publisher;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        TenantContext.setWorkspaceId("workspace-1");
        publisher = new CampaignEventPublisher(eventPublisher);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void publishBatchCreatedPropagatesAsyncKafkaFailure() {
        when(eventPublisher.publish(
                eq(AppConstants.TOPIC_BATCH_CREATED),
                org.mockito.ArgumentMatchers.<EventEnvelope<Map<String, String>>>any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));

        assertThrows(RuntimeException.class,
                () -> publisher.publishBatchCreated("tenant-1", "job-1", "batch-1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishBatchCreatedUsesBoundedPayloadWithoutSubscribers() {
        when(eventPublisher.publish(
                eq(AppConstants.TOPIC_BATCH_CREATED),
                org.mockito.ArgumentMatchers.<EventEnvelope<Map<String, String>>>any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishBatchCreated("tenant-1", "job-1", "batch-1");

        ArgumentCaptor<EventEnvelope<Map<String, String>>> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventPublisher).publish(eq(AppConstants.TOPIC_BATCH_CREATED), envelopeCaptor.capture());
        Map<String, String> payload = envelopeCaptor.getValue().getPayload();
        assertFalse(payload.containsKey("subscribers"));
        assertFalse(payload.containsKey("payload"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishSendProcessingUsesBoundedBatchMetadata() {
        when(eventPublisher.publish(
                eq(AppConstants.TOPIC_SEND_PROCESSING),
                eq("batch-1"),
                org.mockito.ArgumentMatchers.<EventEnvelope<Map<String, String>>>any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishSendProcessing("tenant-1", "job-1", "batch-1", "[{\"email\":\"one@example.com\"}]");

        ArgumentCaptor<EventEnvelope<Map<String, String>>> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventPublisher).publish(eq(AppConstants.TOPIC_SEND_PROCESSING), eq("batch-1"), envelopeCaptor.capture());
        Map<String, String> payload = envelopeCaptor.getValue().getPayload();
        assertFalse(payload.containsKey("subscribers"));
        assertFalse(payload.containsKey("payload"));
    }
}

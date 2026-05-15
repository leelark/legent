package com.legent.deliverability.event;

import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import org.mockito.ArgumentCaptor;

import com.legent.kafka.model.EventEnvelope;
import com.legent.deliverability.domain.SuppressionList;
import com.legent.deliverability.repository.SenderDomainRepository;
import com.legent.deliverability.repository.SuppressionListRepository;
import com.legent.deliverability.service.DeliverabilityEventIdempotencyService;
import com.legent.deliverability.service.ReputationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)

class FeedbackLoopConsumerTest {

    @Mock private SuppressionListRepository suppressionRepository;
    @Mock private SenderDomainRepository senderDomainRepository;
    @Mock private ReputationEngine reputationEngine;
    @Mock private DeliverabilityEventIdempotencyService idempotencyService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private FeedbackLoopConsumer consumer;

    @BeforeEach
    void setup() {
        consumer = new FeedbackLoopConsumer(objectMapper, suppressionRepository, senderDomainRepository, reputationEngine, idempotencyService);
    }

    @Test
    void consumeDeliveryFailedEvents_HardBounce_AddsToSuppression() throws Exception {
        String jsonPayload = "{\"email\":\"bad@example.com\", \"bounceType\":\"HARD\", \"domainId\":\"d1\"}";
        EventEnvelope<String> envelope = EventEnvelope.wrap(AppConstants.TOPIC_EMAIL_BOUNCED, "t1", "test", jsonPayload);
        envelope.setWorkspaceId("w1");

        when(idempotencyService.claimIfNew("t1", "w1", AppConstants.TOPIC_EMAIL_BOUNCED,
                envelope.getEventId(), envelope.getIdempotencyKey())).thenReturn(true);
        when(suppressionRepository.findByTenantIdAndWorkspaceIdAndEmail("t1", "w1", "bad@example.com")).thenReturn(Optional.empty());

        consumer.consumeDeliveryFailedEvents(envelope, AppConstants.TOPIC_EMAIL_BOUNCED);

        ArgumentCaptor<SuppressionList> captor = ArgumentCaptor.forClass(SuppressionList.class);
        verify(suppressionRepository, times(1)).save(captor.capture());

        SuppressionList saved = captor.getValue();
        assertEquals("bad@example.com", saved.getEmail());
        assertEquals("HARD_BOUNCE", saved.getReason());

        verify(reputationEngine).recordNegativeSignal("t1", "w1", "d1", "HARD_BOUNCE");
        verify(idempotencyService).markProcessed("t1", "w1", AppConstants.TOPIC_EMAIL_BOUNCED,
                envelope.getEventId(), envelope.getIdempotencyKey());
    }

    @Test
    void consumeDeliveryFailedEvents_Complaint_AddsToSuppression() {
        String jsonPayload = "{\"email\":\"complaint@example.com\", \"type\":\"SOFT\", \"domainId\":\"d2\"}";
        EventEnvelope<String> envelope = EventEnvelope.wrap(AppConstants.TOPIC_EMAIL_COMPLAINT, "t1", "test", jsonPayload);
        envelope.setWorkspaceId("w1");

        when(idempotencyService.claimIfNew("t1", "w1", AppConstants.TOPIC_EMAIL_COMPLAINT,
                envelope.getEventId(), envelope.getIdempotencyKey())).thenReturn(true);
        when(suppressionRepository.findByTenantIdAndWorkspaceIdAndEmail("t1", "w1", "complaint@example.com"))
                .thenReturn(Optional.empty());

        consumer.consumeDeliveryFailedEvents(envelope, AppConstants.TOPIC_EMAIL_COMPLAINT);

        ArgumentCaptor<SuppressionList> captor = ArgumentCaptor.forClass(SuppressionList.class);
        verify(suppressionRepository, times(1)).save(captor.capture());
        assertEquals("COMPLAINT", captor.getValue().getReason());
        verify(reputationEngine).recordNegativeSignal("t1", "w1", "d2", "COMPLAINT");
        verify(idempotencyService).markProcessed("t1", "w1", AppConstants.TOPIC_EMAIL_COMPLAINT,
                envelope.getEventId(), envelope.getIdempotencyKey());
    }

    @Test
    void consumeDeliveryFailedEvents_SoftBounce_DoesNotSuppress() {
        String jsonPayload = "{\"email\":\"soft@example.com\", \"bounceType\":\"SOFT\", \"domainId\":\"d1\"}";
        EventEnvelope<String> envelope = EventEnvelope.wrap(AppConstants.TOPIC_EMAIL_BOUNCED, "t1", "test", jsonPayload);
        envelope.setWorkspaceId("w1");

        when(idempotencyService.claimIfNew("t1", "w1", AppConstants.TOPIC_EMAIL_BOUNCED,
                envelope.getEventId(), envelope.getIdempotencyKey())).thenReturn(true);

        consumer.consumeDeliveryFailedEvents(envelope, AppConstants.TOPIC_EMAIL_BOUNCED);

        verifyNoInteractions(suppressionRepository, reputationEngine);
        verify(idempotencyService).markProcessed("t1", "w1", AppConstants.TOPIC_EMAIL_BOUNCED,
                envelope.getEventId(), envelope.getIdempotencyKey());
    }

    @Test
    void consumeDeliveryFailedEvents_SuppressionSaveFailure_RethrowsForRetry() {
        String jsonPayload = "{\"email\":\"bad@example.com\", \"bounceType\":\"HARD\", \"domainId\":\"d1\"}";
        EventEnvelope<String> envelope = EventEnvelope.wrap(AppConstants.TOPIC_EMAIL_BOUNCED, "t1", "test", jsonPayload);
        envelope.setWorkspaceId("w1");

        when(idempotencyService.claimIfNew("t1", "w1", AppConstants.TOPIC_EMAIL_BOUNCED,
                envelope.getEventId(), envelope.getIdempotencyKey())).thenReturn(true);
        when(suppressionRepository.findByTenantIdAndWorkspaceIdAndEmail("t1", "w1", "bad@example.com"))
                .thenReturn(Optional.empty());
        when(suppressionRepository.save(any(SuppressionList.class)))
                .thenThrow(new RuntimeException("database unavailable"));

        assertThrows(IllegalStateException.class,
                () -> consumer.consumeDeliveryFailedEvents(envelope, AppConstants.TOPIC_EMAIL_BOUNCED));

        verify(idempotencyService).releaseClaim("t1", "w1", AppConstants.TOPIC_EMAIL_BOUNCED,
                envelope.getEventId(), envelope.getIdempotencyKey());
        verify(idempotencyService, never()).markProcessed(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void consumeDeliveryFailedEvents_ReputationFailure_ReleasesClaimAndRethrowsForRetry() {
        String jsonPayload = "{\"email\":\"bad@example.com\", \"bounceType\":\"HARD\", \"domainId\":\"d1\"}";
        EventEnvelope<String> envelope = EventEnvelope.wrap(AppConstants.TOPIC_EMAIL_BOUNCED, "t1", "test", jsonPayload);
        envelope.setWorkspaceId("w1");

        when(idempotencyService.claimIfNew("t1", "w1", AppConstants.TOPIC_EMAIL_BOUNCED,
                envelope.getEventId(), envelope.getIdempotencyKey())).thenReturn(true);
        when(suppressionRepository.findByTenantIdAndWorkspaceIdAndEmail("t1", "w1", "bad@example.com"))
                .thenReturn(Optional.of(new SuppressionList()));
        doThrow(new RuntimeException("reputation store unavailable"))
                .when(reputationEngine).recordNegativeSignal("t1", "w1", "d1", "HARD_BOUNCE");

        assertThrows(IllegalStateException.class,
                () -> consumer.consumeDeliveryFailedEvents(envelope, AppConstants.TOPIC_EMAIL_BOUNCED));

        verify(idempotencyService).releaseClaim("t1", "w1", AppConstants.TOPIC_EMAIL_BOUNCED,
                envelope.getEventId(), envelope.getIdempotencyKey());
        verify(idempotencyService, never()).markProcessed(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void consumeDeliveryFailedEvents_DuplicateClaim_SkipsSideEffects() {
        String jsonPayload = "{\"email\":\"bad@example.com\", \"bounceType\":\"HARD\", \"domainId\":\"d1\"}";
        EventEnvelope<String> envelope = EventEnvelope.wrap(AppConstants.TOPIC_EMAIL_BOUNCED, "t1", "test", jsonPayload);
        envelope.setWorkspaceId("w1");

        when(idempotencyService.claimIfNew("t1", "w1", AppConstants.TOPIC_EMAIL_BOUNCED,
                envelope.getEventId(), envelope.getIdempotencyKey())).thenReturn(false);

        consumer.consumeDeliveryFailedEvents(envelope, AppConstants.TOPIC_EMAIL_BOUNCED);

        verifyNoInteractions(suppressionRepository, reputationEngine);
        verify(idempotencyService, never()).markProcessed(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(idempotencyService, never()).releaseClaim(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void consumeDeliveryFailedEvents_MalformedPayload_DropsWithoutClaim() {
        EventEnvelope<String> envelope = EventEnvelope.wrap(AppConstants.TOPIC_EMAIL_BOUNCED, "t1", "test", "{not-json");
        envelope.setWorkspaceId("w1");

        consumer.consumeDeliveryFailedEvents(envelope, AppConstants.TOPIC_EMAIL_BOUNCED);

        verifyNoInteractions(idempotencyService, suppressionRepository, reputationEngine);
    }
}

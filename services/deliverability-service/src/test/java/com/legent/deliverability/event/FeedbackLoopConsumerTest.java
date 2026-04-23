package com.legent.deliverability.event;

import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import org.mockito.ArgumentCaptor;

import com.legent.kafka.model.EventEnvelope;
import com.legent.deliverability.domain.SuppressionList;
import com.legent.deliverability.repository.SuppressionListRepository;
import com.legent.deliverability.service.ReputationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)

class FeedbackLoopConsumerTest {

    @Mock private SuppressionListRepository suppressionRepository;
    @Mock private ReputationEngine reputationEngine;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private FeedbackLoopConsumer consumer;

    @BeforeEach
    void setup() {
        consumer = new FeedbackLoopConsumer(objectMapper, suppressionRepository, reputationEngine);
    }

    @Test
    void consumeDeliveryFailedEvents_HardBounce_AddsToSuppression() throws Exception {
        String jsonPayload = "{\"email\":\"bad@example.com\", \"bounceType\":\"HARD\", \"domainId\":\"d1\"}";
        EventEnvelope<String> envelope = EventEnvelope.wrap(AppConstants.TOPIC_EMAIL_BOUNCED, "t1", "test", jsonPayload);

        when(suppressionRepository.findByTenantIdAndEmail("t1", "bad@example.com")).thenReturn(Optional.empty());

        consumer.consumeDeliveryFailedEvents(envelope);

        ArgumentCaptor<SuppressionList> captor = ArgumentCaptor.forClass(SuppressionList.class);
        verify(suppressionRepository, times(1)).save(captor.capture());

        SuppressionList saved = captor.getValue();
        assertEquals("bad@example.com", saved.getEmail());
        assertEquals("HARD_BOUNCE", saved.getReason());

        verify(reputationEngine).recordNegativeSignal("t1", "d1", "HARD_BOUNCE");
    }

    @Test
    void consumeDeliveryFailedEvents_Complaint_AddsToSuppression() {
        String jsonPayload = "{\"email\":\"complaint@example.com\", \"type\":\"SOFT\", \"domainId\":\"d2\"}";
        EventEnvelope<String> envelope = EventEnvelope.wrap(AppConstants.TOPIC_EMAIL_COMPLAINT, "t1", "test", jsonPayload);

        when(suppressionRepository.findByTenantIdAndEmail("t1", "complaint@example.com"))
                .thenReturn(Optional.empty());

        consumer.consumeDeliveryFailedEvents(envelope);

        ArgumentCaptor<SuppressionList> captor = ArgumentCaptor.forClass(SuppressionList.class);
        verify(suppressionRepository, times(1)).save(captor.capture());
        assertEquals("COMPLAINT", captor.getValue().getReason());
        verify(reputationEngine).recordNegativeSignal("t1", "d2", "COMPLAINT");
    }

    @Test
    void consumeDeliveryFailedEvents_SoftBounce_DoesNotSuppress() {
        String jsonPayload = "{\"email\":\"soft@example.com\", \"bounceType\":\"SOFT\", \"domainId\":\"d1\"}";
        EventEnvelope<String> envelope = EventEnvelope.wrap(AppConstants.TOPIC_EMAIL_BOUNCED, "t1", "test", jsonPayload);

        consumer.consumeDeliveryFailedEvents(envelope);

        verifyNoInteractions(suppressionRepository, reputationEngine);
    }
}

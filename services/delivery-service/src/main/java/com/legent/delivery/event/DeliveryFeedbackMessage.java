package com.legent.delivery.event;

import com.legent.kafka.model.EventEnvelope;

import java.util.Map;

public record DeliveryFeedbackMessage(
        String topic,
        String transitionKey,
        String partitionKey,
        String messageId,
        String campaignId,
        String jobId,
        String batchId,
        String subscriberId,
        String recipientEmail,
        String senderDomain,
        EventEnvelope<Map<String, String>> envelope) {
}

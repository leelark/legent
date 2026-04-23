package com.legent.kafka.producer;

import com.legent.kafka.model.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;


/**
 * Generic event publisher for sending domain events to Kafka.
 * All services use this to publish events in a consistent way.
 */
@Slf4j
@Component
@RequiredArgsConstructor

public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes an event envelope to the specified topic.
     * Uses the event's tenantId as the partition key for ordering.
     */
    public <T> CompletableFuture<SendResult<String, Object>> publish(
            @NonNull String topic,
            @NonNull EventEnvelope<T> envelope) {

        String tenantId = envelope.getTenantId();
        String eventId = envelope.getEventId();
        String key = (tenantId != null) ? tenantId : (eventId != null ? eventId : "SYSTEM");

        log.info("Publishing event [{}] to topic [{}] with key [{}]",
                envelope.getEventType(), topic, key);

        return kafkaTemplate.send(topic, key, envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event [{}] to [{}]: {}",
                                envelope.getEventId(), topic, ex.getMessage());
                    } else {
                        log.debug("Event [{}] published to [{}] partition [{}] offset [{}]",
                                envelope.getEventId(),
                                topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Publishes with a custom partition key.
     */
    public <T> CompletableFuture<SendResult<String, Object>> publish(
            @NonNull String topic,
            @NonNull String partitionKey,
            @NonNull EventEnvelope<T> envelope) {

        log.info("Publishing event [{}] to topic [{}] with custom key [{}]",
                envelope.getEventType(), topic, partitionKey);

        String key = (partitionKey != null) ? partitionKey : "SYSTEM";
        return kafkaTemplate.send(topic, key, envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event [{}] to [{}]: {}",
                                envelope.getEventId(), topic, ex.getMessage());
                    }
                });
    }
}

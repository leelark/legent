package com.legent.kafka.producer;

import com.legent.kafka.model.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Generic event publisher for sending domain events to Kafka.
 * All services use this to publish events in a consistent way.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes an event envelope to the specified topic.
     * Uses the event's tenantId as the partition key for ordering.
     */
    public <T> CompletableFuture<SendResult<String, Object>> publish(
            String topic,
            EventEnvelope<T> envelope) {

        String key = envelope.getTenantId() != null ? envelope.getTenantId() : envelope.getEventId();

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
            String topic,
            String partitionKey,
            EventEnvelope<T> envelope) {

        log.info("Publishing event [{}] to topic [{}] with custom key [{}]",
                envelope.getEventType(), topic, partitionKey);

        return kafkaTemplate.send(topic, partitionKey, envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event [{}] to [{}]: {}",
                                envelope.getEventId(), topic, ex.getMessage());
                    }
                });
    }
}

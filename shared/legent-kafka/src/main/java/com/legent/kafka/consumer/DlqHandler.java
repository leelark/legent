package com.legent.kafka.consumer;

import com.legent.kafka.model.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Dead-letter queue handler.
 * Routes failed events to a .dlq topic after max retries are exhausted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class DlqHandler {

    private static final String DLQ_SUFFIX = ".dlq";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Sends a failed event to the dead-letter topic.
     *
     * @param originalTopic the original topic where the event failed
     * @param envelope      the failed event envelope
     * @param error         the exception that caused the failure
     */
    public <T> void sendToDlq(String originalTopic, EventEnvelope<T> envelope, Throwable error) {
        String dlqTopic = originalTopic + DLQ_SUFFIX;

        log.error("Routing event [{}] from [{}] to DLQ [{}] after {} retries. Error: {}",
                envelope.getEventId(),
                originalTopic,
                dlqTopic,
                envelope.getRetryCount(),
                error.getMessage());

        try {
            kafkaTemplate.send(dlqTopic, envelope.getTenantId(), envelope);
        } catch (Exception ex) {
            log.error("CRITICAL: Failed to send to DLQ [{}]: {}", dlqTopic, ex.getMessage(), ex);
            // At this point the event is lost — this should trigger an alert
        }
    }
}

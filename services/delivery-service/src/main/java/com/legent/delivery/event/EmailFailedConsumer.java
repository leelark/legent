package com.legent.delivery.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailFailedConsumer {

    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = AppConstants.TOPIC_EMAIL_FAILED, groupId = AppConstants.GROUP_DELIVERY_FAILED, concurrency = "3")
    public void consumeEmailFailed(String payload, Acknowledgment ack) {
        try {
            log.warn("Received failed email event: {}", payload);
            EventEnvelope<Map<String, Object>> envelope = objectMapper.readValue(
                    payload,
                    new TypeReference<>() {
                    }
            );

            if (envelope.getRetryCount() < 3) {
                EventEnvelope<Map<String, Object>> retryEnvelope = envelope.forRetry();
                eventPublisher.publish(AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED, envelope.getTenantId(), retryEnvelope);
                log.info("Scheduled retry for failed email event [{}] attempt {}", envelope.getEventId(), retryEnvelope.getRetryCount());
            } else {
                eventPublisher.publish(AppConstants.TOPIC_EMAIL_FAILED_DLQ, envelope.getTenantId(), envelope);
                log.warn("Max retry reached for email event [{}], moved to DLQ", envelope.getEventId());
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing failed email event", e);
            try {
                ack.acknowledge();
            } catch (Exception ackException) {
                log.error("Error acknowledging failed email event", ackException);
            }
        }
    }
}

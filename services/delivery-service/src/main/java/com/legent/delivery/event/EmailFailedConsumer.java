package com.legent.delivery.event;

import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "legent.delivery.failed-consumer.enabled", havingValue = "true", matchIfMissing = false)
public class EmailFailedConsumer {

    private final EventPublisher eventPublisher;

    @KafkaListener(topics = AppConstants.TOPIC_EMAIL_FAILED, groupId = AppConstants.GROUP_DELIVERY_FAILED, concurrency = "3")
    public void consumeEmailFailed(EventEnvelope<Map<String, Object>> envelope) {
        try {
            log.warn("Received failed email event: {}", envelope.getEventId());
            if (envelope.getRetryCount() < 3) {
                EventEnvelope<Map<String, Object>> retryEnvelope = envelope.forRetry();
                eventPublisher.publish(AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED, envelope.getTenantId(), retryEnvelope);
                log.info("Scheduled retry for failed email event [{}] attempt {}", envelope.getEventId(), retryEnvelope.getRetryCount());
            } else {
                eventPublisher.publish(AppConstants.TOPIC_EMAIL_FAILED_DLQ, envelope.getTenantId(), envelope);
                log.warn("Max retry reached for email event [{}], moved to DLQ", envelope.getEventId());
            }
        } catch (Exception e) {
            log.error("Error processing failed email event", e);
            throw new RuntimeException(e);
        }
    }
}

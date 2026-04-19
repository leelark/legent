package com.legent.delivery.event;

import com.legent.common.constant.AppConstants;

import java.util.Map;

import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class DeliveryEventPublisher {

    private final EventPublisher eventPublisher;
    private static final String SOURCE = "delivery-service";

    public void publishEmailSent(String tenantId, String messageId, String campaignId, String subscriberId) {
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_SENT, tenantId, SOURCE,
                Map.of(
                        "messageId", messageId,
                        "campaignId", campaignId != null ? campaignId : "",
                        "subscriberId", subscriberId != null ? subscriberId : ""
                )
        );
        eventPublisher.publish(AppConstants.TOPIC_EMAIL_SENT, envelope);
    }

    public void publishEmailFailed(String tenantId, String messageId, String campaignId, String subscriberId, String reason) {
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_FAILED, tenantId, SOURCE,
                Map.of(
                        "messageId", messageId,
                        "campaignId", campaignId != null ? campaignId : "",
                        "subscriberId", subscriberId != null ? subscriberId : "",
                        "reason", reason
                )
        );
        eventPublisher.publish(AppConstants.TOPIC_EMAIL_FAILED, envelope);
    }

    public void publishRetryScheduled(String tenantId, String messageId, long attemptCount, String nextRetryAt) {
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED, tenantId, SOURCE,
                Map.of(
                        "messageId", messageId,
                        "attemptCount", String.valueOf(attemptCount),
                        "nextRetryAt", nextRetryAt
                )
        );
        eventPublisher.publish(AppConstants.TOPIC_EMAIL_RETRY_SCHEDULED, envelope);
    }

    public void publishEmailBounced(String tenantId, String email, String reason) {
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_EMAIL_BOUNCED, tenantId, SOURCE,
                Map.of(
                        "email", email,
                        "reason", reason,
                        "type", "HARD_BOUNCE"
                )
        );
        eventPublisher.publish(AppConstants.TOPIC_EMAIL_BOUNCED, envelope);
    }
}

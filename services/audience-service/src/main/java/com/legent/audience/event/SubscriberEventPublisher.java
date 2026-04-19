package com.legent.audience.event;

import com.legent.common.constant.AppConstants;

import java.util.Map;

import com.legent.audience.domain.Subscriber;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class SubscriberEventPublisher {

    private final EventPublisher eventPublisher;
    private static final String SOURCE = "audience-service";

    public void publishCreated(Subscriber subscriber) {
        publish(AppConstants.TOPIC_SUBSCRIBER_CREATED, subscriber, "CREATED");
    }

    public void publishUpdated(Subscriber subscriber) {
        publish(AppConstants.TOPIC_SUBSCRIBER_UPDATED, subscriber, "UPDATED");
    }

    public void publishDeleted(Subscriber subscriber) {
        publish(AppConstants.TOPIC_SUBSCRIBER_DELETED, subscriber, "DELETED");
    }

    private void publish(String topic, Subscriber subscriber, String action) {
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                topic, subscriber.getTenantId(), SOURCE,
                Map.of("subscriberId", subscriber.getId(),
                        "subscriberKey", subscriber.getSubscriberKey(),
                        "email", subscriber.getEmail(),
                        "action", action)
        );
        eventPublisher.publish(topic, envelope);
    }
}

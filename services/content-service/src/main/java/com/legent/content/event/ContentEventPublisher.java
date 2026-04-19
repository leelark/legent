package com.legent.content.event;

import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentEventPublisher {

    private final EventPublisher eventPublisher;
    private static final String SOURCE = "content-service";

    public void publishTemplatePublished(String tenantId, String templateId, String templateName, String versionNumber) {
        ContentPublishedEvent payload = new ContentPublishedEvent(
                tenantId,
                templateId,
                templateName,
                versionNumber,
                java.time.Instant.now().toString()
        );
        EventEnvelope<ContentPublishedEvent> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_CONTENT_PUBLISHED,
                tenantId,
                SOURCE,
                payload
        );
        eventPublisher.publish(AppConstants.TOPIC_CONTENT_PUBLISHED, envelope);
    }
}

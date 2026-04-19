package com.legent.audience.event;

import com.legent.common.constant.AppConstants;

import java.util.Map;

import com.legent.audience.domain.Segment;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class SegmentEventPublisher {

    private final EventPublisher eventPublisher;
    private static final String SOURCE = "audience-service";

    public void publishCreated(Segment segment) {
        publish(AppConstants.TOPIC_SEGMENT_CREATED, segment, "CREATED");
    }

    public void publishUpdated(Segment segment) {
        publish(AppConstants.TOPIC_SEGMENT_UPDATED, segment, "UPDATED");
    }

    public void publishRecomputed(Segment segment) {
        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_SEGMENT_RECOMPUTED, segment.getTenantId(), SOURCE,
                Map.of("segmentId", segment.getId(),
                        "segmentName", segment.getName(),
                        "memberCount", segment.getMemberCount(),
                        "evaluationMs", segment.getEvaluationDurationMs())
        );
        eventPublisher.publish(AppConstants.TOPIC_SEGMENT_RECOMPUTED, envelope);
    }

    private void publish(String topic, Segment segment, String action) {
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                topic, segment.getTenantId(), SOURCE,
                Map.of("segmentId", segment.getId(), "segmentName", segment.getName(), "action", action)
        );
        eventPublisher.publish(topic, envelope);
    }
}

package com.legent.automation.event;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEventPublisher {

    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private static final String SOURCE = "automation-service";

    public void publishAction(String topic, String tenantId, String jobId, Map<String, Object> payload) {
        try {
            EventEnvelope<String> envelope = EventEnvelope.wrap(
                    topic, tenantId, SOURCE, objectMapper.writeValueAsString(payload)
            );
            eventPublisher.publish(topic, jobId, envelope);
        } catch (Exception e) {
            log.error("Failed to publish workflow action", e);
        }
    }
}

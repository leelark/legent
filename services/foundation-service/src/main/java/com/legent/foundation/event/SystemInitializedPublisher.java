package com.legent.foundation.event;

import com.legent.common.constant.AppConstants;

import java.util.Map;

import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;


/**
 * Publishes the system.initialized event when the Foundation Service starts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemInitializedPublisher {

    private final EventPublisher eventPublisher;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Foundation service ready — publishing system.initialized event");

        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                "system.initialized",
                null,
                "foundation-service",
                Map.of(
                        "service", "foundation-service",
                        "status", "READY"
                )
        );

        eventPublisher.publish(AppConstants.TOPIC_SYSTEM_INITIALIZED, envelope);
    }
}

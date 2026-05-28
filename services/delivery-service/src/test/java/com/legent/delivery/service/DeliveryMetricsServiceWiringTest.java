package com.legent.delivery.service;

import com.legent.delivery.repository.MessageLogRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class DeliveryMetricsServiceWiringTest {

    @Test
    void wiresRetryMetricsServiceWithExplicitProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SimpleMeterRegistry.class);
            context.registerBean(MessageLogRepository.class, () -> mock(MessageLogRepository.class));
            context.register(DeliveryRetryMetricsService.class);

            context.refresh();

            assertNotNull(context.getBean(DeliveryRetryMetricsService.class));
        }
    }
}

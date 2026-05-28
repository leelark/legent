package com.legent.delivery.service;

import com.legent.delivery.domain.MessageLog;
import com.legent.delivery.repository.MessageLogRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryRetryMetricsServiceTest {

    @Mock private MessageLogRepository messageLogRepository;

    @Test
    void exposesRetryAndFailedMessageGaugesWithLowCardinalityLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Instant now = Instant.parse("2026-05-24T09:30:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);

        when(messageLogRepository.countEligibleForRetry(now)).thenReturn(5L);
        when(messageLogRepository.findOldestEligibleRetryAt(now)).thenReturn(Optional.of(now.minusSeconds(300)));
        when(messageLogRepository.countByStatusAndDeletedAtIsNull(MessageLog.DeliveryStatus.FAILED.name()))
                .thenReturn(8L);
        when(messageLogRepository.findOldestCreatedAtByStatus(MessageLog.DeliveryStatus.FAILED.name()))
                .thenReturn(Optional.of(now.minusSeconds(900)));
        when(messageLogRepository.findCountsByJobForStatus(
                MessageLog.DeliveryStatus.FAILED.name(),
                PageRequest.of(0, 1))).thenReturn(List.of(6L));

        DeliveryRetryMetricsService service = new DeliveryRetryMetricsService(registry, messageLogRepository, clock);
        assertNotNull(service);

        assertEquals(5.0, registry.get("legent.retry.ready.depth")
                .tag("queue", "delivery_scheduled_messages")
                .gauge()
                .value());
        assertEquals(300.0, registry.get("legent.retry.oldest.ready.age.seconds")
                .tag("queue", "delivery_scheduled_messages")
                .gauge()
                .value());
        assertEquals(8.0, registry.get("legent.dlq.depth")
                .tag("source", "delivery_failed_messages")
                .gauge()
                .value());
        assertEquals(900.0, registry.get("legent.dlq.oldest.age.seconds")
                .tag("source", "delivery_failed_messages")
                .gauge()
                .value());
        assertEquals(0.75, registry.get("legent.dlq.skew.ratio")
                .tag("source", "delivery_failed_messages")
                .gauge()
                .value(), 0.0001);

        registry.forEachMeter(meter -> assertEquals(1, meter.getId().getTags().size()));
        verify(messageLogRepository).countEligibleForRetry(now);
    }
}

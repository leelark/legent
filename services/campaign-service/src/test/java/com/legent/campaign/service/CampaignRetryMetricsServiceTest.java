package com.legent.campaign.service;

import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.repository.SendBatchRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignRetryMetricsServiceTest {

    @Mock private SendBatchRepository batchRepository;

    @Test
    void exposesPartialAndStaleProcessingRetryGaugesWithoutHighCardinalityLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Instant now = Instant.parse("2026-05-24T09:30:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        Duration leaseTimeout = Duration.ofMinutes(15);
        Instant staleBefore = now.minus(leaseTimeout);

        when(batchRepository.countByStatusAndDeletedAtIsNull(SendBatch.BatchStatus.PARTIAL)).thenReturn(9L);
        when(batchRepository.findOldestUpdatedAtByStatus(SendBatch.BatchStatus.PARTIAL))
                .thenReturn(Optional.of(now.minusSeconds(120)));
        when(batchRepository.countByStatusAndUpdatedAtBeforeAndDeletedAtIsNull(
                SendBatch.BatchStatus.PROCESSING, staleBefore)).thenReturn(4L);
        when(batchRepository.findOldestUpdatedAtByStatusBefore(
                SendBatch.BatchStatus.PROCESSING, staleBefore)).thenReturn(Optional.of(now.minusSeconds(1800)));

        CampaignRetryMetricsService service =
                new CampaignRetryMetricsService(registry, batchRepository, clock, leaseTimeout);
        assertNotNull(service);

        assertEquals(9.0, registry.get("legent.retry.ready.depth")
                .tag("queue", "campaign_partial_batches")
                .gauge()
                .value());
        assertEquals(4.0, registry.get("legent.retry.ready.depth")
                .tag("queue", "campaign_stale_processing_batches")
                .gauge()
                .value());
        assertEquals(120.0, registry.get("legent.retry.oldest.ready.age.seconds")
                .tag("queue", "campaign_partial_batches")
                .gauge()
                .value());
        assertEquals(1800.0, registry.get("legent.retry.oldest.ready.age.seconds")
                .tag("queue", "campaign_stale_processing_batches")
                .gauge()
                .value());

        registry.forEachMeter(meter -> assertEquals(1, meter.getId().getTags().size()));
        verify(batchRepository).countByStatusAndUpdatedAtBeforeAndDeletedAtIsNull(
                eq(SendBatch.BatchStatus.PROCESSING), eq(staleBefore));
    }
}

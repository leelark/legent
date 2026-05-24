package com.legent.campaign.service;

import com.legent.campaign.domain.SendBatch;
import com.legent.campaign.repository.SendBatchRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CampaignRetryMetricsService {

    private static final String PARTIAL_BATCH_QUEUE = "campaign_partial_batches";
    private static final String STALE_PROCESSING_QUEUE = "campaign_stale_processing_batches";

    private final SendBatchRepository batchRepository;
    private final Clock clock;
    private final Duration processingLeaseTimeout;

    public CampaignRetryMetricsService(MeterRegistry meterRegistry,
                                       SendBatchRepository batchRepository,
                                       @Value("${legent.campaign.send.processing-lease-timeout:PT15M}")
                                       Duration processingLeaseTimeout) {
        this(meterRegistry, batchRepository, Clock.systemUTC(), processingLeaseTimeout);
    }

    CampaignRetryMetricsService(MeterRegistry meterRegistry,
                                SendBatchRepository batchRepository,
                                Clock clock,
                                Duration processingLeaseTimeout) {
        this.batchRepository = batchRepository;
        this.clock = clock;
        this.processingLeaseTimeout = processingLeaseTimeout == null ? Duration.ofMinutes(15) : processingLeaseTimeout;
        registerGauges(meterRegistry);
    }

    private void registerGauges(MeterRegistry meterRegistry) {
        Gauge.builder("legent.retry.ready.depth", this, CampaignRetryMetricsService::partialBatchDepth)
                .description("Campaign PARTIAL batches waiting for retry")
                .tag("queue", PARTIAL_BATCH_QUEUE)
                .register(meterRegistry);
        Gauge.builder("legent.retry.ready.depth", this, CampaignRetryMetricsService::staleProcessingBatchDepth)
                .description("Campaign PROCESSING batches older than the processing lease timeout")
                .tag("queue", STALE_PROCESSING_QUEUE)
                .register(meterRegistry);
        Gauge.builder("legent.retry.oldest.ready.age.seconds", this, CampaignRetryMetricsService::partialBatchOldestAgeSeconds)
                .description("Age in seconds of the oldest campaign PARTIAL batch waiting for retry")
                .tag("queue", PARTIAL_BATCH_QUEUE)
                .register(meterRegistry);
        Gauge.builder("legent.retry.oldest.ready.age.seconds", this, CampaignRetryMetricsService::staleProcessingBatchOldestAgeSeconds)
                .description("Age in seconds of the oldest stale campaign PROCESSING batch")
                .tag("queue", STALE_PROCESSING_QUEUE)
                .register(meterRegistry);
    }

    double partialBatchDepth() {
        return safeCount(() -> batchRepository.countByStatusAndDeletedAtIsNull(SendBatch.BatchStatus.PARTIAL));
    }

    double staleProcessingBatchDepth() {
        Instant staleBefore = clock.instant().minus(processingLeaseTimeout);
        return safeCount(() -> batchRepository.countByStatusAndUpdatedAtBeforeAndDeletedAtIsNull(
                SendBatch.BatchStatus.PROCESSING,
                staleBefore));
    }

    double partialBatchOldestAgeSeconds() {
        return safeAgeSeconds(() -> batchRepository.findOldestUpdatedAtByStatus(SendBatch.BatchStatus.PARTIAL));
    }

    double staleProcessingBatchOldestAgeSeconds() {
        Instant staleBefore = clock.instant().minus(processingLeaseTimeout);
        return safeAgeSeconds(() -> batchRepository.findOldestUpdatedAtByStatusBefore(
                SendBatch.BatchStatus.PROCESSING,
                staleBefore));
    }

    private double safeCount(Supplier<Long> supplier) {
        try {
            Long value = supplier.get();
            return value == null ? 0.0 : value.doubleValue();
        } catch (RuntimeException ignored) {
            return 0.0;
        }
    }

    private double safeAgeSeconds(Supplier<Optional<Instant>> supplier) {
        try {
            return supplier.get()
                    .map(this::ageSeconds)
                    .orElse(0.0);
        } catch (RuntimeException ignored) {
            return 0.0;
        }
    }

    private double ageSeconds(Instant instant) {
        if (instant == null) {
            return 0.0;
        }
        return Math.max(0.0, Duration.between(instant, clock.instant()).toSeconds());
    }
}

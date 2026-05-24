package com.legent.delivery.service;

import com.legent.delivery.domain.MessageLog;
import com.legent.delivery.repository.MessageLogRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class DeliveryRetryMetricsService {

    private static final String RETRY_QUEUE = "delivery_scheduled_messages";
    private static final String DLQ_SOURCE = "delivery_failed_messages";

    private final MessageLogRepository messageLogRepository;
    private final Clock clock;

    public DeliveryRetryMetricsService(MeterRegistry meterRegistry,
                                       MessageLogRepository messageLogRepository) {
        this(meterRegistry, messageLogRepository, Clock.systemUTC());
    }

    DeliveryRetryMetricsService(MeterRegistry meterRegistry,
                                MessageLogRepository messageLogRepository,
                                Clock clock) {
        this.messageLogRepository = messageLogRepository;
        this.clock = clock;
        registerGauges(meterRegistry);
    }

    private void registerGauges(MeterRegistry meterRegistry) {
        Gauge.builder("legent.retry.ready.depth", this, DeliveryRetryMetricsService::scheduledRetryDepth)
                .description("Delivery messages whose scheduled retry time is due")
                .tag("queue", RETRY_QUEUE)
                .register(meterRegistry);
        Gauge.builder("legent.retry.oldest.ready.age.seconds", this, DeliveryRetryMetricsService::oldestScheduledRetryAgeSeconds)
                .description("Age in seconds since the oldest due delivery retry became eligible")
                .tag("queue", RETRY_QUEUE)
                .register(meterRegistry);
        Gauge.builder("legent.dlq.depth", this, DeliveryRetryMetricsService::failedMessageDepth)
                .description("Delivery message logs in terminal failed state")
                .tag("source", DLQ_SOURCE)
                .register(meterRegistry);
        Gauge.builder("legent.dlq.oldest.age.seconds", this, DeliveryRetryMetricsService::oldestFailedMessageAgeSeconds)
                .description("Age in seconds of the oldest terminal failed delivery message")
                .tag("source", DLQ_SOURCE)
                .register(meterRegistry);
        Gauge.builder("legent.dlq.skew.ratio", this, DeliveryRetryMetricsService::failedJobSkewRatio)
                .description("Share of failed delivery messages held by the largest job")
                .tag("source", DLQ_SOURCE)
                .register(meterRegistry);
    }

    double scheduledRetryDepth() {
        return safeCount(() -> messageLogRepository.countEligibleForRetry(clock.instant()));
    }

    double oldestScheduledRetryAgeSeconds() {
        return safeAgeSeconds(() -> messageLogRepository.findOldestEligibleRetryAt(clock.instant()));
    }

    double failedMessageDepth() {
        return safeCount(() -> messageLogRepository.countByStatusAndDeletedAtIsNull(MessageLog.DeliveryStatus.FAILED.name()));
    }

    double oldestFailedMessageAgeSeconds() {
        return safeAgeSeconds(() -> messageLogRepository.findOldestCreatedAtByStatus(MessageLog.DeliveryStatus.FAILED.name()));
    }

    double failedJobSkewRatio() {
        try {
            long total = messageLogRepository.countByStatusAndDeletedAtIsNull(MessageLog.DeliveryStatus.FAILED.name());
            if (total <= 0) {
                return 0.0;
            }
            List<Long> counts = messageLogRepository.findCountsByJobForStatus(
                    MessageLog.DeliveryStatus.FAILED.name(),
                    PageRequest.of(0, 1));
            if (counts.isEmpty() || counts.get(0) == null) {
                return 0.0;
            }
            return Math.min(1.0, counts.get(0).doubleValue() / total);
        } catch (RuntimeException ignored) {
            return 0.0;
        }
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

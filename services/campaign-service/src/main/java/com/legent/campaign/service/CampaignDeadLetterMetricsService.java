package com.legent.campaign.service;

import com.legent.campaign.domain.CampaignDeadLetter;
import com.legent.campaign.repository.CampaignDeadLetterRepository;
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
public class CampaignDeadLetterMetricsService {

    private static final String SOURCE = "campaign_dead_letters";

    private final CampaignDeadLetterRepository deadLetterRepository;
    private final Clock clock;

    public CampaignDeadLetterMetricsService(MeterRegistry meterRegistry,
                                            CampaignDeadLetterRepository deadLetterRepository) {
        this(meterRegistry, deadLetterRepository, Clock.systemUTC());
    }

    CampaignDeadLetterMetricsService(MeterRegistry meterRegistry,
                                     CampaignDeadLetterRepository deadLetterRepository,
                                     Clock clock) {
        this.deadLetterRepository = deadLetterRepository;
        this.clock = clock;
        registerGauges(meterRegistry);
    }

    private void registerGauges(MeterRegistry meterRegistry) {
        Gauge.builder("legent.dlq.depth", this, CampaignDeadLetterMetricsService::openDepth)
                .description("Open campaign dead-letter rows waiting for operator review or replay")
                .tag("source", SOURCE)
                .register(meterRegistry);
        Gauge.builder("legent.dlq.oldest.age.seconds", this, CampaignDeadLetterMetricsService::oldestOpenAgeSeconds)
                .description("Age in seconds of the oldest open campaign dead-letter row")
                .tag("source", SOURCE)
                .register(meterRegistry);
        Gauge.builder("legent.dlq.skew.ratio", this, CampaignDeadLetterMetricsService::openJobSkewRatio)
                .description("Share of open campaign dead-letter rows held by the largest job")
                .tag("source", SOURCE)
                .register(meterRegistry);
    }

    double openDepth() {
        return safeCount(() -> deadLetterRepository.countByStatusAndDeletedAtIsNull(CampaignDeadLetter.STATUS_OPEN));
    }

    double oldestOpenAgeSeconds() {
        return safeAgeSeconds(() -> deadLetterRepository.findOldestCreatedAtByStatus(CampaignDeadLetter.STATUS_OPEN));
    }

    double openJobSkewRatio() {
        try {
            long total = deadLetterRepository.countByStatusAndDeletedAtIsNull(CampaignDeadLetter.STATUS_OPEN);
            if (total <= 0) {
                return 0.0;
            }
            List<Long> counts = deadLetterRepository.findOpenCountsByJob(
                    CampaignDeadLetter.STATUS_OPEN,
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

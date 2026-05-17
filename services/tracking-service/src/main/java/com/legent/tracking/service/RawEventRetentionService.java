package com.legent.tracking.service;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class RawEventRetentionService {

    static final int MAX_PURGE_BATCH_SIZE = 50_000;

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;
    private final AtomicBoolean purgeRunning = new AtomicBoolean(false);

    @Value("${legent.tracking.raw-event-retention.enabled:true}")
    private boolean retentionEnabled;

    @Value("${legent.tracking.raw-event-retention.batch-size:10000}")
    private int batchSize;

    public RawEventRetentionService(@Qualifier("jdbcTemplate") JdbcTemplate jdbcTemplate,
                                    MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${legent.tracking.raw-event-retention.interval-ms:3600000}")
    public void purgeExpiredRawEventsOnSchedule() {
        if (!retentionEnabled) {
            return;
        }
        if (!purgeRunning.compareAndSet(false, true)) {
            log.warn("Skipping raw event retention purge because a prior purge is still running");
            return;
        }
        try {
            int deletedRows = purgeExpiredRawEvents();
            if (deletedRows > 0) {
                log.info("Purged {} expired raw tracking event(s) from Postgres", deletedRows);
            }
        } catch (DataAccessException ex) {
            log.error("Raw event retention purge failed", ex);
        } finally {
            purgeRunning.set(false);
        }
    }

    public int purgeExpiredRawEvents() {
        int effectiveBatchSize = effectiveBatchSize();
        long startedNanos = System.nanoTime();
        try {
            Integer deletedRows = jdbcTemplate.queryForObject(
                    "SELECT purge_expired_raw_events(?)",
                    Integer.class,
                    effectiveBatchSize);
            int result = deletedRows == null ? 0 : deletedRows;
            recordPurgeMetrics(startedNanos, "success", result);
            return result;
        } catch (DataAccessException ex) {
            recordPurgeMetrics(startedNanos, "failed", 0);
            throw ex;
        }
    }

    private int effectiveBatchSize() {
        return Math.max(1, Math.min(batchSize, MAX_PURGE_BATCH_SIZE));
    }

    private void recordPurgeMetrics(long startedNanos, String outcome, int deletedRows) {
        Timer.builder("legent.tracking.raw_event_retention.purge.duration")
                .description("Time spent purging expired raw tracking events from Postgres")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(Duration.ofNanos(System.nanoTime() - startedNanos));
        DistributionSummary.builder("legent.tracking.raw_event_retention.purge.rows")
                .description("Rows purged by the raw tracking event Postgres retention function")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(deletedRows);
    }
}

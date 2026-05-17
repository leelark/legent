package com.legent.tracking.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RawEventRetentionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SimpleMeterRegistry meterRegistry;
    private RawEventRetentionService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new RawEventRetentionService(jdbcTemplate, meterRegistry);
        ReflectionTestUtils.setField(service, "retentionEnabled", true);
        ReflectionTestUtils.setField(service, "batchSize", 250_000);
    }

    @Test
    void purgeExpiredRawEvents_CallsBoundedPostgresFunctionAndRecordsMetrics() {
        when(jdbcTemplate.queryForObject(
                "SELECT purge_expired_raw_events(?)",
                Integer.class,
                RawEventRetentionService.MAX_PURGE_BATCH_SIZE))
                .thenReturn(17);

        int deletedRows = service.purgeExpiredRawEvents();

        assertThat(deletedRows).isEqualTo(17);
        assertThat(meterRegistry.get("legent.tracking.raw_event_retention.purge.duration")
                .tag("outcome", "success")
                .timer()
                .count()).isEqualTo(1);
        assertThat(meterRegistry.get("legent.tracking.raw_event_retention.purge.rows")
                .tag("outcome", "success")
                .summary()
                .totalAmount()).isEqualTo(17.0);
    }

    @Test
    void purgeExpiredRawEventsOnSchedule_WhenDisabled_DoesNotCallSql() {
        ReflectionTestUtils.setField(service, "retentionEnabled", false);

        service.purgeExpiredRawEventsOnSchedule();

        verify(jdbcTemplate, never()).queryForObject(
                "SELECT purge_expired_raw_events(?)",
                Integer.class,
                RawEventRetentionService.MAX_PURGE_BATCH_SIZE);
    }
}

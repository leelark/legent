package com.legent.tracking.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingEventIdempotencyMigrationTest {

    @Test
    void v13AddsRawWrittenPhaseAndPendingIndex() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V13__tracking_idempotency_raw_write_phase.sql"));

        assertThat(migration)
                .contains("ALTER TABLE tracking_event_idempotency")
                .contains("ADD COLUMN IF NOT EXISTS raw_written_at TIMESTAMPTZ")
                .contains("CREATE INDEX IF NOT EXISTS idx_tracking_event_idempotency_raw_written_pending")
                .contains("ON tracking_event_idempotency (tenant_id, workspace_id, raw_written_at ASC)")
                .contains("WHERE raw_written_at IS NOT NULL")
                .contains("AND processed_at IS NULL");
    }
}

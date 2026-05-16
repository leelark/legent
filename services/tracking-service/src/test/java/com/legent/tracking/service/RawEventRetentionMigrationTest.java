package com.legent.tracking.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RawEventRetentionMigrationTest {

    @Test
    void migrationDefinesBoundedPostgresRawEventRetention() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V11__raw_event_retention_policy.sql"));

        assertThat(migration)
                .contains("tracking_retention_policies")
                .contains("raw_events_postgres")
                .contains("purge_expired_raw_events")
                .contains("LIMIT batch_size");
    }
}

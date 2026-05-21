package com.legent.tracking.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ClickHouseInitSqlTest {

    @Test
    void localInitSchemaMatchesRawEventWriterLineageColumnsAndRepairsStaleVolumes() throws IOException {
        String sql = clickHouseInitSql();

        assertThat(sql)
                .contains("workspace_id String")
                .contains("experiment_id Nullable(String)")
                .contains("variant_id Nullable(String)")
                .contains("holdout UInt8 DEFAULT 0")
                .contains("workflow_id Nullable(String)")
                .contains("workflow_version Nullable(Int32)")
                .contains("workflow_run_id Nullable(String)")
                .contains("step_id Nullable(String)")
                .contains("path_id Nullable(String)")
                .contains("goal_id Nullable(String)")
                .contains("ALTER TABLE legent_analytics.raw_events ADD COLUMN IF NOT EXISTS workspace_id")
                .contains("ALTER TABLE legent_analytics.raw_events ADD COLUMN IF NOT EXISTS goal_id")
                .contains("ORDER BY (tenant_id, workspace_id, campaign_id, workflow_id, step_id, experiment_id, variant_id, event_type, timestamp, id)")
                .contains("ORDER BY (tenant_id, workspace_id, campaign_id, event_date, event_type)")
                .contains("ORDER BY (tenant_id, workspace_id, subscriber_id, event_date, event_type)");
    }

    private String clickHouseInitSql() throws IOException {
        Path repoRootPath = Path.of("infrastructure", "docker", "local", "clickhouse-init", "init.sql");
        if (Files.exists(repoRootPath)) {
            return Files.readString(repoRootPath);
        }
        return Files.readString(Path.of("..", "..", "infrastructure", "docker", "local", "clickhouse-init", "init.sql"));
    }
}

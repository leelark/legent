package com.legent.tracking.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClickHouseRollupServiceTest {

    @Test
    void datasets_exposeBiRollupContract() {
        ClickHouseRollupService service = new ClickHouseRollupService(emptyProvider());

        assertThat(service.datasets())
                .extracting(row -> row.get("name"))
                .contains("campaign_day_rollups", "raw_events");
        assertThat(service.datasets())
                .filteredOn(row -> "raw_events".equals(row.get("name")))
                .singleElement()
                .satisfies(row -> assertThat((java.util.List<String>) row.get("dimensions"))
                        .contains("workspace_id", "experiment_id", "variant_id", "holdout"));
    }

    @Test
    void rollupSql_containsClickHouseAggregates() {
        ClickHouseRollupService service = new ClickHouseRollupService(emptyProvider());

        assertThat(service.campaignDayRefreshSql())
                .contains("countIf(event_type = 'OPEN')")
                .contains("uniqExact(subscriber_id)");
    }

    @Test
    void rawEventSchema_isPartitionedRetainedAndWorkspaceScoped() {
        ClickHouseRollupService service = new ClickHouseRollupService(emptyProvider());

        assertThat(service.rawEventSchemaStatements().get(0))
                .contains("workspace_id String")
                .contains("experiment_id Nullable(String)")
                .contains("variant_id Nullable(String)")
                .contains("holdout UInt8 DEFAULT 0")
                .contains("ENGINE = MergeTree()")
                .contains("PARTITION BY toYYYYMM(timestamp)")
                .contains("ORDER BY (tenant_id, workspace_id, campaign_id, experiment_id, variant_id, event_type, timestamp, id)")
                .contains("TTL timestamp + INTERVAL 180 DAY DELETE");
    }

    @Test
    void refreshCampaignDayRollups_deletesScopedWindowBeforeInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ClickHouseRollupService service = new ClickHouseRollupService(provider(jdbcTemplate));
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-05-02T00:00:00Z");
        when(jdbcTemplate.update(any(String.class), any(), any(), any(), any()))
                .thenReturn(3)
                .thenReturn(2);

        java.util.Map<String, Object> result = service.refreshCampaignDayRollups("tenant-1", "workspace-1", from, to);

        assertThat(result)
                .containsEntry("status", "REFRESHED")
                .containsEntry("rowsDeleted", 3)
                .containsEntry("rowsWritten", 2);
        var inOrder = inOrder(jdbcTemplate);
        inOrder.verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("DELETE FROM campaign_day_rollups")
                        && sql.contains("tenant_id = ?")
                        && sql.contains("bucket_date >= toDate(?)")),
                eq("tenant-1"),
                eq("workspace-1"),
                any(),
                any());
        inOrder.verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("INSERT INTO campaign_day_rollups")),
                eq("tenant-1"),
                eq("workspace-1"),
                any(),
                any());
    }

    private ObjectProvider<JdbcTemplate> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public JdbcTemplate getObject(Object... args) {
                return null;
            }

            @Override
            public JdbcTemplate getIfAvailable() {
                return null;
            }

            @Override
            public JdbcTemplate getIfUnique() {
                return null;
            }

            @Override
            public JdbcTemplate getObject() {
                return null;
            }
        };
    }

    private ObjectProvider<JdbcTemplate> provider(JdbcTemplate jdbcTemplate) {
        return new ObjectProvider<>() {
            @Override
            public JdbcTemplate getObject(Object... args) {
                return jdbcTemplate;
            }

            @Override
            public JdbcTemplate getIfAvailable() {
                return jdbcTemplate;
            }

            @Override
            public JdbcTemplate getIfUnique() {
                return jdbcTemplate;
            }

            @Override
            public JdbcTemplate getObject() {
                return jdbcTemplate;
            }
        };
    }
}

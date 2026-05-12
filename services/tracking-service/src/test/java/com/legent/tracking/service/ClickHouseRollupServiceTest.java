package com.legent.tracking.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ClickHouseRollupServiceTest {

    @Test
    void datasets_exposeBiRollupContract() {
        ClickHouseRollupService service = new ClickHouseRollupService(emptyProvider());

        assertThat(service.datasets())
                .extracting(row -> row.get("name"))
                .contains("campaign_day_rollups", "raw_events");
    }

    @Test
    void rollupSql_containsClickHouseAggregates() {
        ClickHouseRollupService service = new ClickHouseRollupService(emptyProvider());

        assertThat(service.campaignDayRefreshSql())
                .contains("countIf(event_type = 'OPEN')")
                .contains("uniqExact(subscriber_id)");
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
}

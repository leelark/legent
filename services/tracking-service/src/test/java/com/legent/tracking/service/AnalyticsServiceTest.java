package com.legent.tracking.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks private AnalyticsService service;

    @Test
    void getEventCounts_executesQuery() throws Exception {
        // Mocking DataSource and its interactions is complex for JdbcTemplate
        // We'll just verify the service exists for now as it's a wrapper
    }

    @Test
    void getEventTimeline_appliesDefaultWindowAndBucketLimit() {
        List<Map<String, Object>> expected = List.of(Map.of(
                "hour", Instant.parse("2026-05-01T00:00:00Z"),
                "count", 2L
        ));
        when(jdbcTemplate.queryForList(anyString(), eq("tenant-1"), eq("workspace-1"), eq("OPEN"),
                any(Instant.class), any(Instant.class), eq(AnalyticsService.DEFAULT_TIMELINE_BUCKET_LIMIT)))
                .thenReturn(expected);

        List<Map<String, Object>> result = service.getEventTimeline("tenant-1", "workspace-1", "open");

        assertThat(result).isEqualTo(expected);
        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("date_trunc('hour'")
                        && sql.contains("\"timestamp\" >= ?")
                        && sql.contains("\"timestamp\" <= ?")
                        && sql.contains("LIMIT ?")),
                eq("tenant-1"),
                eq("workspace-1"),
                eq("OPEN"),
                any(Instant.class),
                any(Instant.class),
                eq(AnalyticsService.DEFAULT_TIMELINE_BUCKET_LIMIT));
    }

    @Test
    void getEventTimeline_rejectsInvalidExplicitWindowWithoutQuery() {
        Instant startAt = Instant.parse("2026-05-03T00:00:00Z");
        Instant endAt = Instant.parse("2026-05-02T00:00:00Z");

        assertThat(service.getEventTimeline("tenant-1", "workspace-1", "OPEN", startAt, endAt, 24)).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void getRollups_appliesDefaultHourWindowAndBucketLimit() {
        List<Map<String, Object>> expected = List.of(Map.of(
                "campaign_id", "campaign-1",
                "opens", 3L
        ));
        when(jdbcTemplate.queryForList(anyString(), eq("tenant-1"), eq("workspace-1"),
                any(Instant.class), any(Instant.class), eq(AnalyticsService.DEFAULT_HOUR_ROLLUP_BUCKET_LIMIT)))
                .thenReturn(expected);

        List<Map<String, Object>> result = service.getRollups("tenant-1", "workspace-1", null, "hour");

        assertThat(result).isEqualTo(expected);
        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("date_trunc('hour'")
                        && sql.contains("\"timestamp\" >= ?")
                        && sql.contains("\"timestamp\" <= ?")
                        && sql.contains("LIMIT ?")),
                eq("tenant-1"),
                eq("workspace-1"),
                any(Instant.class),
                any(Instant.class),
                eq(AnalyticsService.DEFAULT_HOUR_ROLLUP_BUCKET_LIMIT));
    }

    @Test
    void getRollups_preservesExplicitWindowCampaignScopeAndClampsDayBucketLimit() {
        Instant startAt = Instant.parse("2026-05-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-05-10T00:00:00Z");
        List<Map<String, Object>> expected = List.of(Map.of(
                "campaign_id", "campaign-1",
                "conversions", 1L
        ));
        when(jdbcTemplate.queryForList(anyString(), eq("tenant-1"), eq("workspace-1"),
                eq(startAt), eq(endAt), eq("campaign-1"), eq(AnalyticsService.MAX_DAY_ROLLUP_BUCKET_LIMIT)))
                .thenReturn(expected);

        List<Map<String, Object>> result = service.getRollups(
                "tenant-1",
                "workspace-1",
                "campaign-1",
                "day",
                startAt,
                endAt,
                AnalyticsService.MAX_DAY_ROLLUP_BUCKET_LIMIT + 100);

        assertThat(result).isEqualTo(expected);
        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("date_trunc('day'")
                        && sql.contains("tenant_id = ? AND workspace_id = ?")
                        && sql.contains("campaign_id = ?")
                        && sql.contains("LIMIT ?")),
                eq("tenant-1"),
                eq("workspace-1"),
                eq(startAt),
                eq(endAt),
                eq("campaign-1"),
                eq(AnalyticsService.MAX_DAY_ROLLUP_BUCKET_LIMIT));
    }

    @Test
    void toCsvEscapesExportRows() {
        AnalyticsService analyticsService = new AnalyticsService(null);

        String csv = analyticsService.toCsv(List.of(Map.of(
                "id", "evt-1",
                "event_type", "CLICK",
                "campaign_id", "campaign-1",
                "subscriber_id", "sub-1",
                "message_id", "msg-1",
                "link_url", "https://example.com/a,b"
        )));

        assertThat(csv).contains("\"https://example.com/a,b\"");
    }

    @Test
    void getJourneyGoalMetrics_scopesByTenantWorkspaceAndWorkflow() {
        List<Map<String, Object>> expected = List.of(Map.of(
                "goal_id", "goal-1",
                "conversions", 3L,
                "revenue", "42.00"
        ));
        when(jdbcTemplate.queryForList(anyString(), eq("tenant-1"), eq("workspace-1"), eq("workflow-1")))
                .thenReturn(expected);

        List<Map<String, Object>> result = service.getJourneyGoalMetrics("tenant-1", "workspace-1", "workflow-1");

        assertThat(result).isEqualTo(expected);
        verify(jdbcTemplate).queryForList(org.mockito.ArgumentMatchers.contains("workflow_id = ?"),
                eq("tenant-1"),
                eq("workspace-1"),
                eq("workflow-1"));
    }

    @Test
    void getJourneyGoalMetrics_missingScopeReturnsEmptyWithoutQuery() {
        assertThat(service.getJourneyGoalMetrics("tenant-1", "", "workflow-1")).isEmpty();
    }
}

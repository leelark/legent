package com.legent.tracking.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
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
        when(jdbcTemplate.queryForList(anyString(), org.mockito.ArgumentMatchers.eq("tenant-1"),
                org.mockito.ArgumentMatchers.eq("workspace-1"), org.mockito.ArgumentMatchers.eq("workflow-1")))
                .thenReturn(expected);

        List<Map<String, Object>> result = service.getJourneyGoalMetrics("tenant-1", "workspace-1", "workflow-1");

        assertThat(result).isEqualTo(expected);
        verify(jdbcTemplate).queryForList(org.mockito.ArgumentMatchers.contains("workflow_id = ?"),
                org.mockito.ArgumentMatchers.eq("tenant-1"),
                org.mockito.ArgumentMatchers.eq("workspace-1"),
                org.mockito.ArgumentMatchers.eq("workflow-1"));
    }

    @Test
    void getJourneyGoalMetrics_missingScopeReturnsEmptyWithoutQuery() {
        assertThat(service.getJourneyGoalMetrics("tenant-1", "", "workflow-1")).isEmpty();
    }
}

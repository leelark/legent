package com.legent.tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.tracking.dto.TrackingDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ClickHouseWriterTest {

    @Test
    void writeBatch_includesWorkspaceLineageInRawEventInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ClickHouseWriter writer = new ClickHouseWriter(jdbcTemplate, new ObjectMapper());
        TrackingDto.RawEventPayload event = TrackingDto.RawEventPayload.builder()
                .id("event-1")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .eventType("open")
                .campaignId("campaign-1")
                .subscriberId("subscriber-1")
                .messageId("message-1")
                .experimentId("experiment-1")
                .variantId("variant-a")
                .holdout(true)
                .experimentScope("JOURNEY")
                .workflowId("workflow-1")
                .workflowVersion(3)
                .workflowRunId("run-1")
                .stepId("step-1")
                .pathId("path-a")
                .goalId("goal-1")
                .timestamp(Instant.parse("2026-05-16T00:00:00Z"))
                .build();
        TrackingDto.RawEventPayload defaultHoldoutEvent = TrackingDto.RawEventPayload.builder()
                .id("event-2")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .eventType("click")
                .campaignId("campaign-1")
                .subscriberId("subscriber-2")
                .messageId("message-2")
                .timestamp(Instant.parse("2026-05-16T00:01:00Z"))
                .build();

        writer.writeBatch(List.of(event, defaultHoldoutEvent));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> argsCaptor = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("workspace_id")
                .contains("experiment_id")
                .contains("variant_id")
                .contains("holdout")
                .contains("workflow_id")
                .contains("goal_id");
        assertThat(argsCaptor.getValue().get(0)[2]).isEqualTo("workspace-1");
        assertThat(argsCaptor.getValue().get(0)[3]).isEqualTo("OPEN");
        assertThat(argsCaptor.getValue().get(0)[7]).isEqualTo("experiment-1");
        assertThat(argsCaptor.getValue().get(0)[8]).isEqualTo("variant-a");
        assertThat(argsCaptor.getValue().get(0)[9]).isEqualTo(true);
        assertThat(argsCaptor.getValue().get(0)[10]).isEqualTo("JOURNEY");
        assertThat(argsCaptor.getValue().get(0)[11]).isEqualTo("workflow-1");
        assertThat(argsCaptor.getValue().get(0)[12]).isEqualTo(3);
        assertThat(argsCaptor.getValue().get(0)[13]).isEqualTo("run-1");
        assertThat(argsCaptor.getValue().get(0)[14]).isEqualTo("step-1");
        assertThat(argsCaptor.getValue().get(0)[15]).isEqualTo("path-a");
        assertThat(argsCaptor.getValue().get(0)[16]).isEqualTo("goal-1");
        assertThat(argsCaptor.getValue().get(1)[3]).isEqualTo("CLICK");
        assertThat(argsCaptor.getValue().get(1)[9]).isEqualTo(false);
    }
}

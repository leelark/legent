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
                .timestamp(Instant.parse("2026-05-16T00:00:00Z"))
                .build();

        writer.writeBatch(List.of(event));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> argsCaptor = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), argsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("workspace_id");
        assertThat(argsCaptor.getValue().get(0)[2]).isEqualTo("workspace-1");
        assertThat(argsCaptor.getValue().get(0)[3]).isEqualTo("OPEN");
    }
}

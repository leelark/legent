package com.legent.tracking.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.kafka.model.EventEnvelope;
import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.service.ClickHouseWriter;
import com.legent.tracking.service.AggregationService;
import com.legent.tracking.service.TrackingEventIdempotencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackingEventConsumerTest {

    @Mock private ClickHouseWriter clickHouseWriter;
    @Mock private AggregationService aggregationService;
    @Mock private TrackingEventIdempotencyService idempotencyService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private TrackingEventConsumer consumer;

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_Success() throws Exception {
        EventEnvelope<String> envelope = new EventEnvelope<>();
        envelope.setTenantId("tenant-1");
        envelope.setWorkspaceId("workspace-1");
        envelope.setEventType("tracking.ingested");
        envelope.setEventId("evt-1");
        envelope.setIdempotencyKey("idem-1");
        envelope.setPayload("{\"tenantId\":\"tenant-1\",\"campaignId\":\"campaign-1\",\"subscriberId\":\"subscriber-1\",\"eventType\":\"OPEN\",\"timestamp\":\"2026-01-01T00:00:00Z\"}");
        List<EventEnvelope<String>> messages = List.of(envelope);

        TrackingDto.RawEventPayload payload = TrackingDto.RawEventPayload.builder()
                .tenantId("tenant-1")
                .campaignId("campaign-1")
                .subscriberId("subscriber-1")
                .eventType("OPEN")
                .timestamp(java.time.Instant.now())
                .build();

        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(payload);
        when(idempotencyService.claimIfNew(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        consumer.handleIngestedEvents(messages);

        verify(aggregationService).aggregateEvent(any());
        verify(clickHouseWriter).writeBatch(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_MissingWorkspace_SkipsEvent() throws Exception {
        EventEnvelope<String> envelope = new EventEnvelope<>();
        envelope.setTenantId("tenant-1");
        envelope.setEventType("tracking.ingested");
        envelope.setEventId("evt-1");
        envelope.setIdempotencyKey("idem-1");
        envelope.setPayload("{\"tenantId\":\"tenant-1\",\"eventType\":\"OPEN\"}");

        TrackingDto.RawEventPayload payload = TrackingDto.RawEventPayload.builder()
                .tenantId("tenant-1")
                .eventType("OPEN")
                .timestamp(java.time.Instant.now())
                .build();

        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(payload);

        consumer.handleIngestedEvents(List.of(envelope));

        verifyNoInteractions(idempotencyService, aggregationService, clickHouseWriter);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_TenantMismatch_SkipsEvent() throws Exception {
        EventEnvelope<String> envelope = new EventEnvelope<>();
        envelope.setTenantId("tenant-envelope");
        envelope.setWorkspaceId("workspace-1");
        envelope.setEventType("tracking.ingested");
        envelope.setEventId("evt-tenant-mismatch");
        envelope.setIdempotencyKey("idem-tenant-mismatch");
        envelope.setPayload("{\"tenantId\":\"tenant-payload\",\"workspaceId\":\"workspace-1\",\"eventType\":\"OPEN\"}");

        TrackingDto.RawEventPayload payload = TrackingDto.RawEventPayload.builder()
                .tenantId("tenant-payload")
                .workspaceId("workspace-1")
                .eventType("OPEN")
                .timestamp(java.time.Instant.now())
                .build();

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);

        consumer.handleIngestedEvents(List.of(envelope));

        verifyNoInteractions(idempotencyService, aggregationService, clickHouseWriter);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_WorkspaceMismatch_SkipsEvent() throws Exception {
        EventEnvelope<String> envelope = new EventEnvelope<>();
        envelope.setTenantId("tenant-1");
        envelope.setWorkspaceId("workspace-envelope");
        envelope.setEventType("tracking.ingested");
        envelope.setEventId("evt-workspace-mismatch");
        envelope.setIdempotencyKey("idem-workspace-mismatch");
        envelope.setPayload("{\"tenantId\":\"tenant-1\",\"workspaceId\":\"workspace-payload\",\"eventType\":\"OPEN\"}");

        TrackingDto.RawEventPayload payload = TrackingDto.RawEventPayload.builder()
                .tenantId("tenant-1")
                .workspaceId("workspace-payload")
                .eventType("OPEN")
                .timestamp(java.time.Instant.now())
                .build();

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);

        consumer.handleIngestedEvents(List.of(envelope));

        verifyNoInteractions(idempotencyService, aggregationService, clickHouseWriter);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_ClickHouseFailure_ReleasesClaimAndRetryProcesses() throws Exception {
        EventEnvelope<String> envelope = new EventEnvelope<>();
        envelope.setTenantId("tenant-1");
        envelope.setWorkspaceId("workspace-1");
        envelope.setEventType("tracking.ingested");
        envelope.setEventId("evt-1");
        envelope.setIdempotencyKey("idem-1");
        envelope.setPayload("{\"tenantId\":\"tenant-1\",\"eventType\":\"OPEN\"}");

        TrackingDto.RawEventPayload payload = TrackingDto.RawEventPayload.builder()
                .tenantId("tenant-1")
                .eventType("OPEN")
                .timestamp(java.time.Instant.now())
                .build();

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);
        when(idempotencyService.claimIfNew(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true, true);
        doThrow(new RuntimeException("clickhouse down"))
                .doNothing()
                .when(clickHouseWriter).writeBatch(anyList());

        assertThrows(IllegalStateException.class, () -> consumer.handleIngestedEvents(List.of(envelope)));
        consumer.handleIngestedEvents(List.of(envelope));

        verify(idempotencyService).releaseClaim("tenant-1", "workspace-1", "OPEN", "evt-1", "idem-1");
        verify(idempotencyService).markProcessed("tenant-1", "workspace-1", "OPEN", "evt-1", "idem-1");
        verify(clickHouseWriter, times(2)).writeBatch(anyList());
        verify(aggregationService).aggregateEvent(any());
    }
}

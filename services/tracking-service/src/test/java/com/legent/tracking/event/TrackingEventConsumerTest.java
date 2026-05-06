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
        when(idempotencyService.registerIfNew(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        consumer.handleIngestedEvents(messages);

        verify(aggregationService).aggregateEvent(any());
        verify(clickHouseWriter).writeBatch(anyList());
    }
}

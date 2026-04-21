package com.legent.tracking.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.kafka.model.EventEnvelope;
import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.service.ClickHouseWriter;
import com.legent.tracking.service.AggregationService;
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
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private TrackingEventConsumer consumer;

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_Success() throws Exception {
        String msg = "{\"payload\":{\"eventType\":\"OPEN\"}}";
        List<String> messages = List.of(msg);

        TrackingDto.RawEventPayload payload = TrackingDto.RawEventPayload.builder()
                .tenantId("tenant-1")
                .campaignId("campaign-1")
                .subscriberId("subscriber-1")
                .eventType("OPEN")
                .timestamp(java.time.Instant.now())
                .build();
        EventEnvelope<TrackingDto.RawEventPayload> envelope = new EventEnvelope<>();
        envelope.setPayload(payload);
        
        when(objectMapper.readValue(eq(msg), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(envelope);

        consumer.handleIngestedEvents(messages);

        verify(aggregationService).aggregateEvent(any());
        verify(clickHouseWriter).writeBatch(anyList());
    }
}

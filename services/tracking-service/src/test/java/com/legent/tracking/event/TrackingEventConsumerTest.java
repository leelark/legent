package com.legent.tracking.event;

import com.fasterxml.jackson.databind.ObjectMapper;
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
@SuppressWarnings("null")
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
        
        when(objectMapper.readValue(eq(msg), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(new com.legent.kafka.model.EventEnvelope<>());

        consumer.handleIngestedEvents(messages);

        verify(clickHouseWriter).writeBatch(anyList());
    }
}

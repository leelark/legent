package com.legent.tracking.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.kafka.model.EventEnvelope;
import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.service.ClickHouseWriter;
import com.legent.tracking.service.AggregationService;
import com.legent.tracking.domain.RawEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingEventConsumer {

    private final ClickHouseWriter clickHouseWriter;
    private final AggregationService aggregationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ingested_events", groupId = "tracking-clickhouse-group")
    public void handleIngestedEvents(List<String> messages) {
        log.info("Received batch of {} tracking events", messages.size());
        List<TrackingDto.RawEventPayload> batch = new ArrayList<>();

        for (String message : messages) {
            try {
                EventEnvelope<TrackingDto.RawEventPayload> envelope = objectMapper.readValue(message, 
                        new TypeReference<EventEnvelope<TrackingDto.RawEventPayload>>() {});
                
                TrackingDto.RawEventPayload payload = envelope.getPayload();
                batch.add(payload);

                // Also perform real-time aggregation for UI summaries
                RawEvent rawEvent = toRawEvent(payload);
                aggregationService.aggregateEvent(rawEvent);

            } catch (Exception e) {
                log.error("Failed to parse tracking event: {}", message, e);
            }
        }

        if (!batch.isEmpty()) {
            try {
                clickHouseWriter.writeBatch(batch);
            } catch (Exception e) {
                log.error("Failed to write batch to ClickHouse", e);
            }
        }
    }

    private RawEvent toRawEvent(TrackingDto.RawEventPayload p) {
        RawEvent e = new RawEvent();
        e.setTenantId(p.getTenantId());
        e.setCampaignId(p.getCampaignId());
        e.setSubscriberId(p.getSubscriberId());
        e.setEventType(p.getEventType());
        e.setTimestamp(p.getTimestamp());
        return e;
    }
}

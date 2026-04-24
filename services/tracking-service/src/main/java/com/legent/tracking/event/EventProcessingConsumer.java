package com.legent.tracking.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.legent.kafka.model.EventEnvelope;
import com.legent.security.TenantContext;
import com.legent.tracking.domain.RawEvent;
import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.repository.RawEventRepository;
import com.legent.tracking.service.AggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventProcessingConsumer {

    private final ObjectMapper objectMapper;
    private final RawEventRepository rawEventRepository;
    private final AggregationService aggregationService;

    @KafkaListener(topics = AppConstants.TOPIC_TRACKING_INGESTED, groupId = AppConstants.GROUP_TRACKING, concurrency = "3")
    public void consumeIngestedEvent(EventEnvelope<String> event) {
        if (event.getTenantId() == null || event.getTenantId().isBlank()) {
            log.error("Received tracking event missing tenant context: {}", event.getEventId());
            return;
        }
        try {
            TenantContext.setTenantId(event.getTenantId());
            
            TrackingDto.RawEventPayload payload = objectMapper.readValue(event.getPayload(), TrackingDto.RawEventPayload.class);
            
            // 1. Persist to Raw Store
            RawEvent raw = new RawEvent();
            raw.setId(payload.getId());
            raw.setTenantId(payload.getTenantId());
            raw.setEventType(payload.getEventType());
            raw.setCampaignId(payload.getCampaignId());
            raw.setSubscriberId(payload.getSubscriberId());
            raw.setMessageId(payload.getMessageId());
            raw.setUserAgent(payload.getUserAgent());
            raw.setIpAddress(payload.getIpAddress());
            raw.setLinkUrl(payload.getLinkUrl());
            raw.setTimestamp(payload.getTimestamp());
            if (payload.getMetadata() != null && !payload.getMetadata().isEmpty()) {
                raw.setMetadata(objectMapper.writeValueAsString(payload.getMetadata()));
            }

            rawEventRepository.save(raw);

            // 2. Aggregate
            aggregationService.aggregateEvent(raw);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse tracking payload", e);
        } catch (Exception e) {
            log.error("Failed to process event {}", event.getEventId(), e);
        } finally {
            TenantContext.clear();
        }
    }
}

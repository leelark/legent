package com.legent.tracking.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.service.ClickHouseWriter;
import com.legent.tracking.service.AggregationService;
import com.legent.tracking.service.TrackingEventIdempotencyService;
import com.legent.tracking.domain.RawEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingEventConsumer {

    private final ClickHouseWriter clickHouseWriter;
    private final AggregationService aggregationService;
    private final TrackingEventIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = AppConstants.TOPIC_TRACKING_INGESTED, groupId = "tracking-clickhouse-group")
    public void handleIngestedEvent(EventEnvelope<String> event) {
        handleIngestedEvents(List.of(event));
    }

    void handleIngestedEvents(List<EventEnvelope<String>> events) {
        if (events == null || events.isEmpty()) {
            log.info("Received empty tracking event batch");
            return;
        }

        log.info("Received batch of {} tracking events", events.size());
        List<ClaimedTrackingEvent> claimedEvents = new ArrayList<>();

        for (EventEnvelope<String> event : events) {
            try {
                if (event == null || event.getPayload() == null || event.getPayload().isBlank()) {
                    log.warn("Skipping tracking event with missing payload");
                    continue;
                }

                TrackingDto.RawEventPayload payload = objectMapper.readValue(
                        event.getPayload(),
                        new TypeReference<TrackingDto.RawEventPayload>() {}
                );
                String tenantId = normalizeRequiredScope(event.getTenantId());
                String workspaceId = normalizeRequiredScope(event.getWorkspaceId());
                if (tenantId == null || workspaceId == null) {
                    log.error("Skipping tracking event with missing workspaceId. eventId={}, tenantId={}",
                            event.getEventId(), event.getTenantId());
                    continue;
                }
                if (scopeMismatch("tenantId", tenantId, payload.getTenantId(), event.getEventId())
                        || scopeMismatch("workspaceId", workspaceId, payload.getWorkspaceId(), event.getEventId())) {
                    continue;
                }

                String eventType = normalizeEventType(payload.getEventType());
                if (eventType == null) {
                    log.warn("Skipping tracking event with missing tenantId or eventType");
                    continue;
                }
                payload.setEventType(eventType);
                payload.setTenantId(tenantId);
                payload.setWorkspaceId(workspaceId);
                if (payload.getOwnershipScope() == null || payload.getOwnershipScope().isBlank()) {
                    payload.setOwnershipScope("WORKSPACE");
                }
                String idempotencyKey = firstNonBlank(payload.getIdempotencyKey(), event.getIdempotencyKey(), payload.getId());
                if (idempotencyKey != null) {
                    payload.setIdempotencyKey(idempotencyKey);
                }
                if (payload.getId() == null || payload.getId().isBlank()) {
                    payload.setId(event.getEventId());
                }
                String eventId = firstNonBlank(event.getEventId(), payload.getId(), idempotencyKey);

                if (!idempotencyService.claimIfNew(
                        tenantId,
                        workspaceId,
                        eventType,
                        eventId,
                        idempotencyKey)) {
                    continue;
                }
                claimedEvents.add(new ClaimedTrackingEvent(payload, tenantId, workspaceId, eventType, eventId, idempotencyKey));

            } catch (JsonProcessingException e) {
                log.warn("Dropping malformed tracking event. eventId={}", event == null ? "unknown" : event.getEventId(), e);
            } catch (Exception e) {
                log.error("Failed to process tracking event. eventId={}", event == null ? "unknown" : event.getEventId(), e);
                throw new IllegalStateException("Failed to process tracking event", e);
            }
        }

        if (!claimedEvents.isEmpty()) {
            try {
                List<TrackingDto.RawEventPayload> batch = claimedEvents.stream()
                        .map(ClaimedTrackingEvent::payload)
                        .toList();
                clickHouseWriter.writeBatch(batch);
                for (ClaimedTrackingEvent claimed : claimedEvents) {
                    aggregationService.aggregateEvent(toRawEvent(claimed.payload()));
                    idempotencyService.markProcessed(
                            claimed.tenantId(),
                            claimed.workspaceId(),
                            claimed.eventType(),
                            claimed.eventId(),
                            claimed.idempotencyKey());
                }
            } catch (Exception e) {
                releaseClaims(claimedEvents, e);
                log.error("Failed to write tracking event side effects", e);
                throw new IllegalStateException("Failed to process tracking event side effects", e);
            }
        }
    }

    private void releaseClaims(List<ClaimedTrackingEvent> claimedEvents, Exception processingFailure) {
        for (ClaimedTrackingEvent claimed : claimedEvents) {
            try {
                idempotencyService.releaseClaim(
                        claimed.tenantId(),
                        claimed.workspaceId(),
                        claimed.eventType(),
                        claimed.eventId(),
                        claimed.idempotencyKey());
            } catch (Exception releaseFailure) {
                processingFailure.addSuppressed(releaseFailure);
                log.error("Failed to release tracking idempotency claim eventId={}", claimed.eventId(), releaseFailure);
            }
        }
    }

    private String normalizeEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }
        return eventType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRequiredScope(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean scopeMismatch(String field, String envelopeValue, String payloadValue, String eventId) {
        if (payloadValue == null || payloadValue.isBlank()) {
            return false;
        }
        String normalizedPayloadValue = payloadValue.trim();
        if (Objects.equals(envelopeValue, normalizedPayloadValue)) {
            return false;
        }
        log.error("Skipping tracking event with {} mismatch. eventId={}, envelope={}, payload={}",
                field, eventId, envelopeValue, normalizedPayloadValue);
        return true;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private RawEvent toRawEvent(TrackingDto.RawEventPayload p) {
        RawEvent e = new RawEvent();
        e.setId(p.getId());
        e.setTenantId(p.getTenantId());
        e.setWorkspaceId(p.getWorkspaceId());
        e.setTeamId(p.getTeamId());
        e.setOwnershipScope(p.getOwnershipScope() == null ? "WORKSPACE" : p.getOwnershipScope());
        e.setCampaignId(p.getCampaignId());
        e.setSubscriberId(p.getSubscriberId());
        e.setMessageId(p.getMessageId());
        e.setExperimentId(p.getExperimentId());
        e.setVariantId(p.getVariantId());
        e.setHoldout(Boolean.TRUE.equals(p.getHoldout()));
        e.setEventType(p.getEventType().trim().toUpperCase(java.util.Locale.ROOT));
        e.setUserAgent(p.getUserAgent());
        e.setIpAddress(p.getIpAddress());
        e.setLinkUrl(p.getLinkUrl());
        e.setTimestamp(p.getTimestamp() == null ? java.time.Instant.now() : p.getTimestamp());
        if (p.getMetadata() != null && !p.getMetadata().isEmpty()) {
            try {
                e.setMetadata(objectMapper.writeValueAsString(p.getMetadata()));
            } catch (Exception ex) {
                log.warn("Unable to serialize tracking metadata for event {}", p.getId(), ex);
            }
        }
        return e;
    }

    private record ClaimedTrackingEvent(
            TrackingDto.RawEventPayload payload,
            String tenantId,
            String workspaceId,
            String eventType,
            String eventId,
            String idempotencyKey) {
    }
}

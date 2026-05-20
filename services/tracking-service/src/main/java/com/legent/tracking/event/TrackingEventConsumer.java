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

        try {
            for (EventEnvelope<String> event : events) {
                TrackingEnvelopeContract contract = requireEnvelopeContract(event);
                TrackingDto.RawEventPayload payload = parsePayload(event, contract.eventId());
                requireScopeMatch("tenantId", contract.tenantId(), payload.getTenantId(), contract.eventId());
                requireScopeMatch("workspaceId", contract.workspaceId(), payload.getWorkspaceId(), contract.eventId());

                String eventType = requireEventType(payload.getEventType(), contract.eventId());
                payload.setEventType(eventType);
                payload.setTenantId(contract.tenantId());
                payload.setWorkspaceId(contract.workspaceId());
                if (payload.getOwnershipScope() == null || payload.getOwnershipScope().isBlank()) {
                    payload.setOwnershipScope("WORKSPACE");
                }
                String idempotencyKey = firstNonBlank(payload.getIdempotencyKey(), event.getIdempotencyKey(), payload.getId());
                if (idempotencyKey != null) {
                    payload.setIdempotencyKey(idempotencyKey);
                }
                if (payload.getId() == null || payload.getId().isBlank()) {
                    payload.setId(contract.eventId());
                }
                String eventId = firstNonBlank(contract.eventId(), payload.getId(), idempotencyKey);

                if (!idempotencyService.claimIfNew(
                        contract.tenantId(),
                        contract.workspaceId(),
                        eventType,
                        eventId,
                        idempotencyKey)) {
                    continue;
                }
                claimedEvents.add(new ClaimedTrackingEvent(payload, contract.tenantId(), contract.workspaceId(), eventType, eventId, idempotencyKey));
            }
        } catch (Exception e) {
            releaseClaims(claimedEvents, e);
            log.error("Failed to process tracking event batch", e);
            throw new IllegalStateException("Failed to process tracking event", e);
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

    private TrackingEnvelopeContract requireEnvelopeContract(EventEnvelope<String> event) {
        if (event == null) {
            throw new IllegalArgumentException("Tracking event envelope is required");
        }
        String eventId = requireNonBlank(event.getEventId(), "eventId", "unknown");
        String envelopeEventType = requireNonBlank(event.getEventType(), "eventType", eventId);
        if (!AppConstants.TOPIC_TRACKING_INGESTED.equals(envelopeEventType)) {
            throw new IllegalArgumentException("tracking event envelope eventType must be "
                    + AppConstants.TOPIC_TRACKING_INGESTED + " for eventId=" + eventId);
        }
        String tenantId = requireNonBlank(event.getTenantId(), "tenantId", eventId);
        String workspaceId = requireNonBlank(event.getWorkspaceId(), "workspaceId", eventId);
        requireNonBlank(event.getPayload(), "payload", eventId);
        return new TrackingEnvelopeContract(eventId, tenantId, workspaceId);
    }

    private TrackingDto.RawEventPayload parsePayload(EventEnvelope<String> event, String eventId) {
        try {
            TrackingDto.RawEventPayload payload = objectMapper.readValue(
                    event.getPayload(),
                    new TypeReference<TrackingDto.RawEventPayload>() {}
            );
            if (payload == null) {
                throw new IllegalArgumentException("tracking payload is required for eventId=" + eventId);
            }
            return payload;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("tracking payload must be valid JSON for eventId=" + eventId, e);
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

    private String requireEventType(String eventType, String eventId) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("tracking payload eventType is required for eventId=" + eventId);
        }
        return eventType.trim().toUpperCase(Locale.ROOT);
    }

    private String requireNonBlank(String value, String field, String eventId) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tracking event " + field + " is required for eventId=" + eventId);
        }
        return value.trim();
    }

    private void requireScopeMatch(String field, String envelopeValue, String payloadValue, String eventId) {
        if (payloadValue == null || payloadValue.isBlank()) {
            return;
        }
        String normalizedPayloadValue = payloadValue.trim();
        if (Objects.equals(envelopeValue, normalizedPayloadValue)) {
            return;
        }
        throw new IllegalArgumentException("tracking event " + field + " mismatch for eventId=" + eventId);
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
        e.setExperimentScope(p.getExperimentScope());
        e.setWorkflowId(p.getWorkflowId());
        e.setWorkflowVersion(p.getWorkflowVersion());
        e.setWorkflowRunId(p.getWorkflowRunId());
        e.setStepId(p.getStepId());
        e.setPathId(p.getPathId());
        e.setGoalId(p.getGoalId());
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

    private record TrackingEnvelopeContract(
            String eventId,
            String tenantId,
            String workspaceId) {
    }
}

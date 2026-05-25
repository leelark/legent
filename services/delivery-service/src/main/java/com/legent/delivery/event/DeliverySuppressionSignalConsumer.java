package com.legent.delivery.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import com.legent.delivery.domain.SuppressionSignal;
import com.legent.delivery.service.DeliveryEventIdempotencyService;
import com.legent.delivery.service.SuppressionSignalService;
import com.legent.kafka.model.EventEnvelope;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "legent.delivery.suppression-signal-consumer.enabled", havingValue = "true", matchIfMissing = true)
public class DeliverySuppressionSignalConsumer {

    private final ObjectMapper objectMapper;
    private final SuppressionSignalService suppressionSignalService;
    private final DeliveryEventIdempotencyService idempotencyService;

    @KafkaListener(
            topics = {
                    AppConstants.TOPIC_EMAIL_BOUNCED,
                    AppConstants.TOPIC_EMAIL_COMPLAINT,
                    AppConstants.TOPIC_EMAIL_UNSUBSCRIBED
            },
            groupId = AppConstants.GROUP_DELIVERY + "-suppression-signals",
            concurrency = "3")
    public void consumeSuppressionSignal(EventEnvelope<?> envelope,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic) {
        String tenantId = envelope == null ? null : envelope.getTenantId();
        String workspaceId = null;
        String eventType = envelope == null ? null : envelope.getEventType();
        String eventId = envelope == null ? null : envelope.getEventId();
        String idempotencyKey = envelope == null ? null : envelope.getIdempotencyKey();
        boolean claimed = false;
        try {
            if (envelope == null) {
                throw new InvalidSuppressionSignalException("Missing suppression signal envelope");
            }
            tenantId = requireTenantId(envelope);
            Map<String, Object> payload = toPayloadMap(envelope.getPayload());
            workspaceId = resolveWorkspaceId(envelope, payload);
            eventType = resolveEventType(envelope.getEventType(), receivedTopic);
            if (!hasClaimKey(eventId, idempotencyKey)) {
                throw new InvalidSuppressionSignalException("Missing eventId or idempotencyKey for suppression signal event");
            }
            SuppressionSignal.SignalType signalType = signalType(eventType, payload);
            String email = requirePayloadValue(payload, "email", envelope.getEventId());
            String sourceMessageId = firstNonBlank(
                    value(payload.get("messageId")),
                    value(payload.get("sourceMessageId")));
            String reason = firstNonBlank(value(payload.get("reason")), signalType.name());

            claimed = idempotencyService.claimIfNew(tenantId, workspaceId, eventType, eventId, idempotencyKey);
            if (!claimed) {
                return;
            }

            suppressionSignalService.recordSignal(tenantId, workspaceId, email, signalType, reason, sourceMessageId);
            idempotencyService.markProcessed(tenantId, workspaceId, eventType, eventId, idempotencyKey);
        } catch (InvalidSuppressionSignalException | IllegalArgumentException e) {
            if (claimed) {
                releaseClaim(tenantId, workspaceId, eventType, eventId, idempotencyKey, e);
            }
            log.warn("Rejecting invalid delivery suppression signal eventId={}, reason={}",
                    eventId == null ? "unknown" : eventId, e.getMessage());
            throw new IllegalStateException("Invalid delivery suppression signal event", e);
        } catch (Exception e) {
            if (claimed) {
                releaseClaim(tenantId, workspaceId, eventType, eventId, idempotencyKey, e);
            }
            log.error("Failed to process delivery suppression signal eventId={}", eventId, e);
            throw new IllegalStateException("Failed to process delivery suppression signal event " + eventId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toPayloadMap(Object payload) {
        if (payload == null) {
            throw new InvalidSuppressionSignalException("Suppression signal payload is required");
        }
        if (payload instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (payload instanceof String text) {
            if (text.isBlank()) {
                throw new InvalidSuppressionSignalException("Suppression signal payload is required");
            }
            try {
                return objectMapper.readValue(text, new TypeReference<>() {});
            } catch (Exception e) {
                throw new InvalidSuppressionSignalException("Suppression signal payload is not valid JSON", e);
            }
        }
        return objectMapper.convertValue(payload, new TypeReference<>() {});
    }

    private SuppressionSignal.SignalType signalType(String eventType, Map<String, Object> payload) {
        if (AppConstants.TOPIC_EMAIL_COMPLAINT.equals(eventType)) {
            return SuppressionSignal.SignalType.COMPLAINT;
        }
        if (AppConstants.TOPIC_EMAIL_UNSUBSCRIBED.equals(eventType)) {
            return SuppressionSignal.SignalType.UNSUBSCRIBE;
        }
        String bounceType = firstNonBlank(value(payload.get("bounceType")), value(payload.get("type")));
        if (bounceType == null) {
            throw new InvalidSuppressionSignalException("bounceType or type is required for bounced suppression signal");
        }
        return bounceType.toUpperCase(java.util.Locale.ROOT).contains("SOFT")
                ? SuppressionSignal.SignalType.SOFT_BOUNCE
                : SuppressionSignal.SignalType.HARD_BOUNCE;
    }

    private String resolveEventType(String envelopeEventType, String receivedTopic) {
        String eventType = firstNonBlank(value(envelopeEventType), value(receivedTopic));
        if (eventType == null) {
            throw new InvalidSuppressionSignalException("Missing eventType for suppression signal event");
        }
        if (!AppConstants.TOPIC_EMAIL_BOUNCED.equals(eventType)
                && !AppConstants.TOPIC_EMAIL_COMPLAINT.equals(eventType)
                && !AppConstants.TOPIC_EMAIL_UNSUBSCRIBED.equals(eventType)) {
            throw new InvalidSuppressionSignalException("Unsupported suppression signal event type: " + eventType);
        }
        if (receivedTopic != null && !receivedTopic.isBlank() && !eventType.equals(receivedTopic.trim())) {
            throw new InvalidSuppressionSignalException("Event type/topic mismatch: eventType=" + eventType + ", topic=" + receivedTopic);
        }
        return eventType;
    }

    private String resolveWorkspaceId(EventEnvelope<?> envelope, Map<String, Object> payload) {
        String envelopeWorkspaceId = value(envelope.getWorkspaceId());
        String payloadWorkspaceId = value(payload.get("workspaceId"));
        if (envelopeWorkspaceId != null && payloadWorkspaceId != null && !envelopeWorkspaceId.equals(payloadWorkspaceId)) {
            throw new InvalidSuppressionSignalException("workspaceId mismatch for suppression signal event " + envelope.getEventId());
        }
        String workspaceId = firstNonBlank(envelopeWorkspaceId, payloadWorkspaceId);
        if (workspaceId == null) {
            throw new InvalidSuppressionSignalException("Missing workspaceId for suppression signal event " + envelope.getEventId());
        }
        return workspaceId;
    }

    private String requireTenantId(EventEnvelope<?> envelope) {
        String tenantId = value(envelope.getTenantId());
        if (tenantId == null) {
            throw new InvalidSuppressionSignalException("Missing tenantId for suppression signal event " + envelope.getEventId());
        }
        return tenantId;
    }

    private String requirePayloadValue(Map<String, Object> payload, String field, String eventId) {
        String parsed = value(payload.get(field));
        if (parsed == null) {
            throw new InvalidSuppressionSignalException(field + " is required for suppression signal event " + eventId);
        }
        return parsed;
    }

    private boolean hasClaimKey(String eventId, String idempotencyKey) {
        return value(eventId) != null || value(idempotencyKey) != null;
    }

    private String firstNonBlank(String first, String second) {
        return first != null ? first : second;
    }

    private String value(Object value) {
        if (value == null) {
            return null;
        }
        String parsed = String.valueOf(value).trim();
        return parsed.isEmpty() ? null : parsed;
    }

    private void releaseClaim(String tenantId,
                              String workspaceId,
                              String eventType,
                              String eventId,
                              String idempotencyKey,
                              Exception processingFailure) {
        try {
            idempotencyService.releaseClaim(tenantId, workspaceId, eventType, eventId, idempotencyKey);
        } catch (Exception releaseFailure) {
            processingFailure.addSuppressed(releaseFailure);
            log.error("Failed to release suppression signal idempotency claim eventId={}", eventId, releaseFailure);
        }
    }

    private static class InvalidSuppressionSignalException extends RuntimeException {
        private InvalidSuppressionSignalException(String message) {
            super(message);
        }

        private InvalidSuppressionSignalException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

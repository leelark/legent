package com.legent.foundation.event;

import com.legent.common.constant.AppConstants;
import com.legent.foundation.service.TenantBootstrapService;
import com.legent.kafka.model.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantBootstrapConsumer {

    private final TenantBootstrapService tenantBootstrapService;

    @KafkaListener(topics = AppConstants.TOPIC_TENANT_BOOTSTRAP_REQUESTED, groupId = AppConstants.GROUP_FOUNDATION_BOOTSTRAP)
    public void consumeBootstrap(EventEnvelope<?> envelope) {
        try {
            validateBootstrapEnvelope(envelope);
            Map<String, Object> payload = payloadMap(envelope);
            String tenantId = envelope.getTenantId();
            validatePayloadTenant(payload, tenantId, envelope);
            String organizationName = payload.get("organizationName") instanceof String value ? value : null;
            String organizationSlug = payload.get("organizationSlug") instanceof String value ? value : null;
            boolean force = payload.get("force") instanceof Boolean value && value;
            tenantBootstrapService.bootstrapTenant(tenantId, organizationName, organizationSlug, force);
        } catch (Exception ex) {
            log.error("Failed bootstrap request eventId={}", eventId(envelope), ex);
            throw ex;
        }
    }

    private void validateBootstrapEnvelope(EventEnvelope<?> envelope) {
        validateRequiredEnvelopeIdentity(envelope, AppConstants.TOPIC_TENANT_BOOTSTRAP_REQUESTED);
        if (envelope.getPayload() == null) {
            throw invalidEnvelope("payload is required", envelope);
        }
    }

    private void validateRequiredEnvelopeIdentity(EventEnvelope<?> envelope, String expectedEventType) {
        if (envelope == null) {
            throw invalidEnvelope("envelope is required", null);
        }
        if (isBlank(envelope.getTenantId())) {
            throw invalidEnvelope("tenantId is required", envelope);
        }
        if (isBlank(envelope.getEventId())) {
            throw invalidEnvelope("eventId is required", envelope);
        }
        if (isBlank(envelope.getEventType())) {
            throw invalidEnvelope("eventType is required", envelope);
        }
        if (!expectedEventType.equals(envelope.getEventType())) {
            throw invalidEnvelope("eventType must be " + expectedEventType, envelope);
        }
    }

    private Map<String, Object> payloadMap(EventEnvelope<?> envelope) {
        if (!(envelope.getPayload() instanceof Map<?, ?> rawPayload)) {
            throw invalidEnvelope("payload must be an object", envelope);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        rawPayload.forEach((key, value) -> {
            if (key != null) {
                payload.put(String.valueOf(key), value);
            }
        });
        return payload;
    }

    private void validatePayloadTenant(Map<String, Object> payload, String tenantId, EventEnvelope<?> envelope) {
        String payloadTenantId = stringValue(payload.get("tenantId"));
        if (payloadTenantId != null && !tenantId.equals(payloadTenantId)) {
            throw invalidEnvelope("payload tenantId must match envelope tenantId", envelope);
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private IllegalArgumentException invalidEnvelope(String reason, EventEnvelope<?> envelope) {
        return new IllegalArgumentException("Invalid bootstrap envelope: " + reason
                + " (eventId=" + eventId(envelope) + ")");
    }

    private String eventId(EventEnvelope<?> envelope) {
        return envelope == null || isBlank(envelope.getEventId()) ? "unknown" : envelope.getEventId();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

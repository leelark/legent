package com.legent.delivery.event;

import com.legent.common.constant.AppConstants;

import java.util.Map;

import com.legent.delivery.service.DeliveryOrchestrationService;
import com.legent.delivery.service.DeliveryEventIdempotencyService;
import com.legent.kafka.model.EventEnvelope;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventConsumer {

    private final DeliveryOrchestrationService orchestrationService;
    private final DeliveryEventIdempotencyService idempotencyService;

    @KafkaListener(topics = AppConstants.TOPIC_EMAIL_SEND_REQUESTED, groupId = AppConstants.GROUP_DELIVERY, concurrency = "5")
    public void handleSendRequest(EventEnvelope<Map<String, Object>> event) {
        String tenantId = event != null ? event.getTenantId() : null;
        String workspaceId = null;
        String eventType = event != null && event.getEventType() != null ? event.getEventType() : AppConstants.TOPIC_EMAIL_SEND_REQUESTED;
        String eventId = event != null ? event.getEventId() : null;
        String idempotencyKey = event != null ? event.getIdempotencyKey() : null;
        boolean claimed = false;
        try {
            Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Map.of();
            workspaceId = resolveWorkspaceId(event, payload);
            Map<String, Object> scopedPayload = new HashMap<>(payload);
            scopedPayload.put("workspaceId", workspaceId);
            claimed = idempotencyService.claimIfNew(
                    tenantId,
                    workspaceId,
                    eventType,
                    eventId,
                    idempotencyKey);
            if (!claimed) {
                return;
            }
            TenantContext.setTenantId(tenantId);
            TenantContext.setWorkspaceId(workspaceId);
            if (event.getEnvironmentId() != null && !event.getEnvironmentId().isBlank()) {
                TenantContext.setEnvironmentId(event.getEnvironmentId());
            }
            if (event.getActorId() != null && !event.getActorId().isBlank()) {
                TenantContext.setUserId(event.getActorId());
            }
            if (event.getIdempotencyKey() != null && !event.getIdempotencyKey().isBlank()) {
                TenantContext.setRequestId(event.getIdempotencyKey());
            }
            orchestrationService.processSendRequest(scopedPayload, tenantId, eventId);
            idempotencyService.markProcessed(tenantId, workspaceId, eventType, eventId, idempotencyKey);
        } catch (Exception e) {
            if (claimed) {
                releaseClaim(tenantId, workspaceId, eventType, eventId, idempotencyKey, e);
            }
            String loggedEventId = eventId != null ? eventId : "<unknown>";
            log.error("Error processing email send request {}", loggedEventId, e);
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Error processing email send request " + loggedEventId, e);
        } finally {
            TenantContext.clear();
        }
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
            log.error("Failed to release delivery event idempotency claim eventId={}", eventId, releaseFailure);
        }
    }

    private String resolveWorkspaceId(EventEnvelope<Map<String, Object>> event, Map<String, Object> payload) {
        String envelopeWorkspaceId = normalize(event.getWorkspaceId());
        String payloadWorkspaceId = normalize(payload.get("workspaceId"));
        if (envelopeWorkspaceId != null && payloadWorkspaceId != null && !Objects.equals(envelopeWorkspaceId, payloadWorkspaceId)) {
            throw new IllegalArgumentException("workspaceId mismatch between event envelope and payload for event " + event.getEventId());
        }
        if (envelopeWorkspaceId != null) {
            return envelopeWorkspaceId;
        }
        if (payloadWorkspaceId != null) {
            return payloadWorkspaceId;
        }
        throw new IllegalArgumentException("Missing workspaceId in event envelope/payload for event " + event.getEventId());
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String parsed = String.valueOf(value).trim();
        return parsed.isEmpty() ? null : parsed;
    }
}

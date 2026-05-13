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


@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventConsumer {

    private final DeliveryOrchestrationService orchestrationService;
    private final DeliveryEventIdempotencyService idempotencyService;

    @KafkaListener(topics = AppConstants.TOPIC_EMAIL_SEND_REQUESTED, groupId = AppConstants.GROUP_DELIVERY, concurrency = "5")
    public void handleSendRequest(EventEnvelope<Map<String, Object>> event) {
        try {
            Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Map.of();
            String workspaceId = resolveWorkspaceId(event, payload);
            String eventType = event.getEventType() != null ? event.getEventType() : AppConstants.TOPIC_EMAIL_SEND_REQUESTED;
            if (!idempotencyService.registerIfNew(
                    event.getTenantId(),
                    workspaceId,
                    eventType,
                    event.getEventId(),
                    event.getIdempotencyKey())) {
                return;
            }
            TenantContext.setTenantId(event.getTenantId());
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
            orchestrationService.processSendRequest(payload, event.getTenantId(), event.getEventId());
        } catch (Exception e) {
            String eventId = event != null ? event.getEventId() : "<unknown>";
            log.error("Error processing email send request {}", eventId, e);
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Error processing email send request " + eventId, e);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveWorkspaceId(EventEnvelope<Map<String, Object>> event, Map<String, Object> payload) {
        if (event.getWorkspaceId() != null && !event.getWorkspaceId().isBlank()) {
            return event.getWorkspaceId();
        }
        Object fromPayload = payload.get("workspaceId");
        if (fromPayload != null) {
            String parsed = String.valueOf(fromPayload).trim();
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }
        throw new IllegalArgumentException("Missing workspaceId in event envelope/payload for event " + event.getEventId());
    }
}

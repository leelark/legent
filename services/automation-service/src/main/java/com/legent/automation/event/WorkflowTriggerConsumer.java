package com.legent.automation.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;

import java.util.Map;

import com.legent.kafka.model.EventEnvelope;
import com.legent.automation.service.AutomationEventIdempotencyService;
import com.legent.automation.service.WorkflowEngine;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowTriggerConsumer {

    private final ObjectMapper objectMapper;
    private final WorkflowEngine workflowEngine;
    private final AutomationEventIdempotencyService idempotencyService;

    @KafkaListener(topics = AppConstants.TOPIC_WORKFLOW_TRIGGER, groupId = AppConstants.GROUP_AUTOMATION)
    @Transactional
    public void consumeTrigger(EventEnvelope<Object> event) {
        String tenantId = null;
        String workspaceId = null;
        String eventId = null;
        String idempotencyKey = null;
        boolean claimed = false;
        try {
            if (event == null) {
                log.warn("Dropping null workflow.trigger event");
                return;
            }
            tenantId = event.getTenantId();
            workspaceId = event.getWorkspaceId();
            eventId = event.getEventId();
            idempotencyKey = event.getIdempotencyKey();

            if (!AppConstants.TOPIC_WORKFLOW_TRIGGER.equals(event.getEventType())) {
                log.warn("Ignoring non-canonical automation trigger eventType: {}", event.getEventType());
                return;
            }
            if (tenantId == null || tenantId.isBlank()) {
                log.error("Dropping workflow.trigger event without tenantId. eventId={}", eventId);
                return;
            }
            if (workspaceId == null || workspaceId.isBlank()) {
                log.error("Dropping workflow.trigger event without workspaceId. eventId={}", eventId);
                return;
            }
            if (isBlank(eventId) && isBlank(idempotencyKey)) {
                log.error("Dropping workflow.trigger event without eventId or idempotencyKey. tenant={}, workspace={}",
                        tenantId, workspaceId);
                return;
            }

            Map<String, Object> payload = toPayloadMap(event.getPayload());
            String workflowId = asString(payload.get("workflowId"));
            Integer version = asInteger(payload.get("version"));
            String subscriberId = asString(payload.get("subscriberId"));
            @SuppressWarnings("unchecked")
            Map<String, Object> context = payload.get("context") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();

            if (workflowId == null || workflowId.isBlank()) {
                log.error("Dropping workflow.trigger without workflowId. eventId={}", eventId);
                return;
            }
            if (subscriberId == null || subscriberId.isBlank()) {
                log.error("Dropping workflow.trigger without subscriberId. eventId={}", eventId);
                return;
            }
            if (!idempotencyService.claimIfNew(
                    tenantId,
                    workspaceId,
                    AppConstants.TOPIC_WORKFLOW_TRIGGER,
                    eventId,
                    idempotencyKey
            )) {
                return;
            }
            claimed = true;

            TenantContext.setTenantId(tenantId);
            TenantContext.setWorkspaceId(workspaceId);
            TenantContext.setEnvironmentId(event.getEnvironmentId());
            TenantContext.setUserId(event.getActorId());
            String requestId = !isBlank(idempotencyKey) ? idempotencyKey : eventId;
            TenantContext.setRequestId(requestId);
            TenantContext.setCorrelationId(event.getCorrelationId());

            workflowEngine.startWorkflow(
                    tenantId,
                    workspaceId,
                    workflowId,
                    version,
                    subscriberId,
                    context,
                    event.getEnvironmentId(),
                    event.getActorId(),
                    requestId,
                    event.getCorrelationId()
            );
            idempotencyService.markProcessed(
                    tenantId,
                    workspaceId,
                    AppConstants.TOPIC_WORKFLOW_TRIGGER,
                    eventId,
                    idempotencyKey
            );
        } catch (JsonProcessingException e) {
            log.warn("Dropping malformed workflow.trigger event. eventId={}", eventId == null ? "unknown" : eventId, e);
        } catch (Exception e) {
            if (claimed) {
                releaseClaim(tenantId, workspaceId, AppConstants.TOPIC_WORKFLOW_TRIGGER, eventId, idempotencyKey, e);
            }
            log.error("Failed to trigger workflow execution. eventId={}", eventId == null ? "unknown" : eventId, e);
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to trigger workflow execution", e);
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
            log.error("Failed to release automation workflow.trigger idempotency claim eventId={}", eventId, releaseFailure);
        }
    }

    private Map<String, Object> toPayloadMap(Object payload) throws Exception {
        if (payload == null) {
            return Map.of();
        }
        if (payload instanceof String rawJson) {
            return objectMapper.readValue(rawJson, new TypeReference<>() {});
        }
        if (payload instanceof Map<?, ?> mapPayload) {
            return objectMapper.convertValue(mapPayload, new TypeReference<>() {});
        }
        return objectMapper.convertValue(payload, new TypeReference<>() {});
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

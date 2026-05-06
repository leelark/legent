package com.legent.automation.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;

import java.util.Map;

import com.legent.kafka.model.EventEnvelope;
import com.legent.automation.service.AutomationEventIdempotencyService;
import com.legent.automation.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowTriggerConsumer {

    private final ObjectMapper objectMapper;
    private final WorkflowEngine workflowEngine;
    private final AutomationEventIdempotencyService idempotencyService;

    @KafkaListener(topics = AppConstants.TOPIC_WORKFLOW_TRIGGER, groupId = AppConstants.GROUP_AUTOMATION)
    public void consumeTrigger(EventEnvelope<Object> event) {
        try {
            if (!AppConstants.TOPIC_WORKFLOW_TRIGGER.equals(event.getEventType())) {
                log.warn("Ignoring non-canonical automation trigger eventType: {}", event.getEventType());
                return;
            }
            if (event.getTenantId() == null || event.getTenantId().isBlank()) {
                log.error("Dropping workflow.trigger event without tenantId. eventId={}", event.getEventId());
                return;
            }
            if (event.getWorkspaceId() == null || event.getWorkspaceId().isBlank()) {
                log.error("Dropping workflow.trigger event without workspaceId. eventId={}", event.getEventId());
                return;
            }
            if (!idempotencyService.registerIfNew(
                    event.getTenantId(),
                    event.getWorkspaceId(),
                    AppConstants.TOPIC_WORKFLOW_TRIGGER,
                    event.getEventId(),
                    event.getIdempotencyKey()
            )) {
                return;
            }

            com.legent.security.TenantContext.setTenantId(event.getTenantId());
            com.legent.security.TenantContext.setWorkspaceId(event.getWorkspaceId());
            com.legent.security.TenantContext.setEnvironmentId(event.getEnvironmentId());
            com.legent.security.TenantContext.setUserId(event.getActorId());
            String requestId = event.getIdempotencyKey() != null && !event.getIdempotencyKey().isBlank()
                    ? event.getIdempotencyKey()
                    : event.getEventId();
            com.legent.security.TenantContext.setRequestId(requestId);
            com.legent.security.TenantContext.setCorrelationId(event.getCorrelationId());

            Map<String, Object> payload = toPayloadMap(event.getPayload());
            String workflowId = asString(payload.get("workflowId"));
            Integer version = asInteger(payload.get("version"));
            String subscriberId = asString(payload.get("subscriberId"));
            @SuppressWarnings("unchecked")
            Map<String, Object> context = payload.get("context") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();

            if (workflowId == null || workflowId.isBlank()) {
                log.error("Dropping workflow.trigger without workflowId. eventId={}", event.getEventId());
                return;
            }
            if (subscriberId == null || subscriberId.isBlank()) {
                log.error("Dropping workflow.trigger without subscriberId. eventId={}", event.getEventId());
                return;
            }

            workflowEngine.startWorkflow(
                    event.getTenantId(),
                    event.getWorkspaceId(),
                    workflowId,
                    version,
                    subscriberId,
                    context,
                    event.getEnvironmentId(),
                    event.getActorId(),
                    requestId,
                    event.getCorrelationId()
            );
        } catch (Exception e) {
            log.error("Failed to parse or trigger workflow execution", e);
        } finally {
            com.legent.security.TenantContext.clear();
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
}

package com.legent.platform.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;

import java.util.Map;

import com.legent.kafka.model.EventEnvelope;
import com.legent.security.TenantContext;
import com.legent.platform.service.GlobalSearchService;
import com.legent.platform.service.NotificationEngine;
import com.legent.platform.service.WebhookDispatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformEventConsumer {

    private final ObjectMapper objectMapper;
    private final WebhookDispatcherService webhookDispatcherService;
    private final NotificationEngine notificationEngine;
    private final GlobalSearchService searchService;

    @KafkaListener(topics = {AppConstants.TOPIC_WEBHOOK_TRIGGERED}, groupId = AppConstants.GROUP_PLATFORM)
    public void consumeWebhookTriggers(EventEnvelope<String> event) {
        try {
            Map<String, Object> payload = readPayload(event, AppConstants.TOPIC_WEBHOOK_TRIGGERED);
            EventContext context = setEventContext(event, payload, AppConstants.TOPIC_WEBHOOK_TRIGGERED);
            
            String eventTypeToDispatch = (String) payload.get("eventToDispatch");
            Object data = payload.get("data");

            webhookDispatcherService.dispatch(context.tenantId(), eventTypeToDispatch, data);

        } catch (Exception e) {
            log.error("Failed to process webhook trigger", e);
            throw new IllegalStateException("Failed to process webhook trigger", e);
        } finally {
            TenantContext.clear();
        }
    }

    @KafkaListener(topics = {AppConstants.TOPIC_NOTIFICATION_CREATED}, groupId = AppConstants.GROUP_PLATFORM)
    public void consumeNotificationEvents(EventEnvelope<String> event) {
        try {
            Map<String, Object> payload = readPayload(event, AppConstants.TOPIC_NOTIFICATION_CREATED);
            EventContext context = setEventContext(event, payload, AppConstants.TOPIC_NOTIFICATION_CREATED);
            
            String userId = (String) payload.get("userId");
            String title = (String) payload.get("title");
            String message = (String) payload.get("message");
            String severity = (String) payload.get("severity");
            String linkUrl = (String) payload.get("linkUrl");

            notificationEngine.createNotification(context.tenantId(), userId, title, message, severity, linkUrl);

        } catch (Exception e) {
            log.error("Failed to process notification creation", e);
            throw new IllegalStateException("Failed to process notification creation", e);
        } finally {
            TenantContext.clear();
        }
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = {AppConstants.TOPIC_SEARCH_INDEX_UPDATED}, groupId = AppConstants.GROUP_PLATFORM)
    public void consumeSearchIndexUpdates(EventEnvelope<String> event) {
        try {
            Map<String, Object> payload = readPayload(event, AppConstants.TOPIC_SEARCH_INDEX_UPDATED);
            EventContext context = setEventContext(event, payload, AppConstants.TOPIC_SEARCH_INDEX_UPDATED);
            
            String entityType = (String) payload.get("entityType");
            String entityId = (String) payload.get("entityId");
            String title = (String) payload.get("title");
            String searchableText = (String) payload.get("searchableText");
            Map<String, Object> metadata = (Map<String, Object>) payload.get("metadata");

            searchService.indexDocument(context.tenantId(), entityType, entityId, title, searchableText, metadata);

        } catch (Exception e) {
            log.error("Failed to update abstract search index", e);
            throw new IllegalStateException("Failed to update abstract search index", e);
        } finally {
            TenantContext.clear();
        }
    }

    private Map<String, Object> readPayload(EventEnvelope<String> event, String expectedEventType) throws Exception {
        if (event == null) {
            throw new IllegalArgumentException(expectedEventType + " event envelope is required");
        }
        if (event.getPayload() == null || event.getPayload().isBlank()) {
            throw new IllegalArgumentException("payload is required for " + expectedEventType);
        }
        Map<String, Object> payload = objectMapper.readValue(event.getPayload(), new TypeReference<>() {});
        if (payload == null) {
            throw new IllegalArgumentException("payload must be a JSON object for " + expectedEventType);
        }
        return payload;
    }

    private EventContext setEventContext(EventEnvelope<?> event, Map<String, Object> payload, String expectedEventType) {
        String eventType = normalize(event.getEventType());
        if (eventType == null) {
            throw new IllegalArgumentException("eventType is required for " + expectedEventType);
        }
        if (!expectedEventType.equals(eventType)) {
            throw new IllegalArgumentException("eventType [" + eventType + "] must match topic [" + expectedEventType + "]");
        }
        if (firstNonBlank(event.getEventId(), event.getIdempotencyKey()) == null) {
            throw new IllegalArgumentException("eventId or idempotencyKey is required for " + expectedEventType);
        }

        String tenantId = normalize(event.getTenantId());
        if (tenantId == null) {
            throw new IllegalArgumentException("Event tenantId is required");
        }
        String payloadTenantId = findStringValue(payload, "tenantId");
        if (payloadTenantId != null && !tenantId.equals(payloadTenantId)) {
            throw new IllegalArgumentException("tenantId mismatch between envelope and payload");
        }
        TenantContext.setTenantId(tenantId);

        String workspaceId = resolveWorkspaceId(event, payload);
        if (isWorkspaceScoped(event, payload) && workspaceId == null) {
            throw new IllegalArgumentException("Workspace-scoped platform event requires workspaceId");
        }
        if (workspaceId != null) {
            TenantContext.setWorkspaceId(workspaceId);
        }

        String environmentId = firstNonBlank(event.getEnvironmentId(), stringValue(payload.get("environmentId")));
        if (environmentId != null) {
            TenantContext.setEnvironmentId(environmentId);
        }
        return new EventContext(tenantId);
    }

    private String resolveWorkspaceId(EventEnvelope<?> event, Map<String, Object> payload) {
        String envelopeWorkspaceId = normalize(event.getWorkspaceId());
        String payloadWorkspaceId = findStringValue(payload, "workspaceId");
        if (envelopeWorkspaceId != null && payloadWorkspaceId != null
                && !envelopeWorkspaceId.equals(payloadWorkspaceId)) {
            throw new IllegalArgumentException("workspaceId mismatch between envelope and payload");
        }
        return envelopeWorkspaceId != null ? envelopeWorkspaceId : payloadWorkspaceId;
    }

    private boolean isWorkspaceScoped(EventEnvelope<?> event, Map<String, Object> payload) {
        return "WORKSPACE".equalsIgnoreCase(String.valueOf(event.getOwnershipScope()))
                || "WORKSPACE".equalsIgnoreCase(String.valueOf(findStringValue(payload, "ownershipScope")));
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = normalize(first);
        return normalizedFirst != null ? normalizedFirst : normalize(second);
    }

    private String findStringValue(Map<String, Object> payload, String key) {
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getKey() != null && key.equalsIgnoreCase(entry.getKey())) {
                return stringValue(entry.getValue());
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record EventContext(String tenantId) {
    }
}

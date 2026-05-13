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
            TenantContext.setTenantId(event.getTenantId());
            Map<String, Object> payload = objectMapper.readValue(event.getPayload(), new TypeReference<>() {});
            
            String eventTypeToDispatch = (String) payload.get("eventToDispatch");
            Object data = payload.get("data");

            webhookDispatcherService.dispatch(event.getTenantId(), eventTypeToDispatch, data);

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
            TenantContext.setTenantId(event.getTenantId());
            Map<String, Object> payload = objectMapper.readValue(event.getPayload(), new TypeReference<>() {});
            
            String userId = (String) payload.get("userId");
            String title = (String) payload.get("title");
            String message = (String) payload.get("message");
            String severity = (String) payload.get("severity");
            String linkUrl = (String) payload.get("linkUrl");

            notificationEngine.createNotification(event.getTenantId(), userId, title, message, severity, linkUrl);

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
            TenantContext.setTenantId(event.getTenantId());
            Map<String, Object> payload = objectMapper.readValue(event.getPayload(), new TypeReference<>() {});
            
            String entityType = (String) payload.get("entityType");
            String entityId = (String) payload.get("entityId");
            String title = (String) payload.get("title");
            String searchableText = (String) payload.get("searchableText");
            Map<String, Object> metadata = (Map<String, Object>) payload.get("metadata");

            searchService.indexDocument(event.getTenantId(), entityType, entityId, title, searchableText, metadata);

        } catch (Exception e) {
            log.error("Failed to update abstract search index", e);
            throw new IllegalStateException("Failed to update abstract search index", e);
        } finally {
            TenantContext.clear();
        }
    }
}

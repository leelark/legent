package com.legent.platform.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.platform.service.GlobalSearchService;
import com.legent.platform.service.NotificationEngine;
import com.legent.platform.service.WebhookDispatcherService;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PlatformEventConsumerTest {

    @Mock private WebhookDispatcherService webhookDispatcherService;
    @Mock private NotificationEngine notificationEngine;
    @Mock private GlobalSearchService searchService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void consumeWebhookTriggers_whenDispatchAcceptanceFails_rethrowsAndClearsTenantContext() {
        PlatformEventConsumer consumer = new PlatformEventConsumer(
                new ObjectMapper(), webhookDispatcherService, notificationEngine, searchService);
        doThrow(new IllegalStateException("retry store unavailable"))
                .when(webhookDispatcherService).dispatch(eq("t1"), eq("email.bounced"), any());

        EventEnvelope<String> event = platformEvent(
                AppConstants.TOPIC_WEBHOOK_TRIGGERED,
                "{\"eventToDispatch\":\"email.bounced\",\"data\":{\"id\":\"m1\"}}");

        assertThrows(IllegalStateException.class, () -> consumer.consumeWebhookTriggers(event));
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void consumeWebhookTriggers_setsWorkspaceContextFromEnvelopeDuringDispatch() {
        PlatformEventConsumer consumer = new PlatformEventConsumer(
                new ObjectMapper(), webhookDispatcherService, notificationEngine, searchService);
        doAnswer(invocation -> {
            assertEquals("t1", TenantContext.getTenantId());
            assertEquals("w1", TenantContext.getWorkspaceId());
            assertEquals("e1", TenantContext.getEnvironmentId());
            return null;
        }).when(webhookDispatcherService).dispatch(eq("t1"), eq("email.bounced"), any());

        EventEnvelope<String> event = platformEvent(
                AppConstants.TOPIC_WEBHOOK_TRIGGERED,
                "t1",
                "w1",
                "e1",
                "WORKSPACE",
                "{\"eventToDispatch\":\"email.bounced\",\"data\":{\"id\":\"m1\"}}");

        consumer.consumeWebhookTriggers(event);

        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
        assertNull(TenantContext.getEnvironmentId());
    }

    @Test
    void consumeWebhookTriggers_rejectsWorkspaceScopedEventWithoutWorkspaceId() {
        PlatformEventConsumer consumer = new PlatformEventConsumer(
                new ObjectMapper(), webhookDispatcherService, notificationEngine, searchService);
        EventEnvelope<String> event = platformEvent(
                AppConstants.TOPIC_WEBHOOK_TRIGGERED,
                "t1",
                null,
                null,
                "WORKSPACE",
                "{\"eventToDispatch\":\"email.bounced\",\"data\":{\"id\":\"m1\"}}");

        assertThrows(IllegalStateException.class, () -> consumer.consumeWebhookTriggers(event));
        verifyNoInteractions(webhookDispatcherService, notificationEngine, searchService);
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void consumeNotificationEvents_rejectsMissingTenantIdWithoutSideEffects() {
        PlatformEventConsumer consumer = new PlatformEventConsumer(
                new ObjectMapper(), webhookDispatcherService, notificationEngine, searchService);
        EventEnvelope<String> event = platformEvent(
                AppConstants.TOPIC_NOTIFICATION_CREATED,
                null,
                "w1",
                null,
                "WORKSPACE",
                "{\"userId\":\"user-1\",\"title\":\"Title\",\"message\":\"Message\",\"severity\":\"INFO\",\"linkUrl\":\"/app\"}");

        assertThrows(IllegalStateException.class, () -> consumer.consumeNotificationEvents(event));

        verifyNoInteractions(webhookDispatcherService, notificationEngine, searchService);
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
    }

    @Test
    void consumeNotificationEvents_rejectsPayloadWorkspaceScopeWithoutWorkspaceId() {
        PlatformEventConsumer consumer = new PlatformEventConsumer(
                new ObjectMapper(), webhookDispatcherService, notificationEngine, searchService);
        EventEnvelope<String> event = platformEvent(
                AppConstants.TOPIC_NOTIFICATION_CREATED,
                "{\"ownershipScope\":\"WORKSPACE\",\"userId\":\"user-1\",\"title\":\"Title\",\"message\":\"Message\",\"severity\":\"INFO\",\"linkUrl\":\"/app\"}");

        assertThrows(IllegalStateException.class, () -> consumer.consumeNotificationEvents(event));

        verifyNoInteractions(webhookDispatcherService, notificationEngine, searchService);
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
    }

    @Test
    void consumeNotificationEvents_setsWorkspaceContextDuringCreation() {
        PlatformEventConsumer consumer = new PlatformEventConsumer(
                new ObjectMapper(), webhookDispatcherService, notificationEngine, searchService);
        doAnswer(invocation -> {
            assertEquals("t1", TenantContext.getTenantId());
            assertEquals("w1", TenantContext.getWorkspaceId());
            return null;
        }).when(notificationEngine).createNotification(
                eq("t1"), eq("user-1"), eq("Title"), eq("Message"), eq("INFO"), eq("/app"));

        EventEnvelope<String> event = platformEvent(
                AppConstants.TOPIC_NOTIFICATION_CREATED,
                "t1",
                "w1",
                null,
                "WORKSPACE",
                "{\"userId\":\"user-1\",\"title\":\"Title\",\"message\":\"Message\",\"severity\":\"INFO\",\"linkUrl\":\"/app\"}");

        consumer.consumeNotificationEvents(event);

        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
    }

    @Test
    void consumeSearchIndexUpdates_whenIndexingFails_rethrowsAndClearsTenantContext() {
        PlatformEventConsumer consumer = new PlatformEventConsumer(
                new ObjectMapper(), webhookDispatcherService, notificationEngine, searchService);
        doThrow(new IllegalStateException("metadata serialization failed"))
                .when(searchService).indexDocument(eq("t1"), eq("CAMPAIGN"), eq("c1"), eq("Campaign"), eq("copy"), any());

        EventEnvelope<String> event = platformEvent(
                AppConstants.TOPIC_SEARCH_INDEX_UPDATED,
                "{\"entityType\":\"CAMPAIGN\",\"entityId\":\"c1\",\"title\":\"Campaign\",\"searchableText\":\"copy\",\"metadata\":{\"key\":\"value\"}}");

        assertThrows(IllegalStateException.class, () -> consumer.consumeSearchIndexUpdates(event));
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void consumeSearchIndexUpdates_setsWorkspaceContextDuringIndexing() {
        PlatformEventConsumer consumer = new PlatformEventConsumer(
                new ObjectMapper(), webhookDispatcherService, notificationEngine, searchService);
        doAnswer(invocation -> {
            assertEquals("t1", TenantContext.getTenantId());
            assertEquals("w1", TenantContext.getWorkspaceId());
            return null;
        }).when(searchService).indexDocument(
                eq("t1"), eq("CAMPAIGN"), eq("c1"), eq("Campaign"), eq("copy"), any());

        EventEnvelope<String> event = platformEvent(
                AppConstants.TOPIC_SEARCH_INDEX_UPDATED,
                "t1",
                "w1",
                null,
                "WORKSPACE",
                "{\"entityType\":\"CAMPAIGN\",\"entityId\":\"c1\",\"title\":\"Campaign\",\"searchableText\":\"copy\",\"metadata\":{\"key\":\"value\"}}");

        consumer.consumeSearchIndexUpdates(event);

        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
    }

    private EventEnvelope<String> platformEvent(String topic, String payload) {
        return platformEvent(topic, "t1", null, null, null, payload);
    }

    private EventEnvelope<String> platformEvent(
            String topic,
            String tenantId,
            String workspaceId,
            String environmentId,
            String ownershipScope,
            String payload) {
        return EventEnvelope.<String>builder()
                .eventId("event-1")
                .eventType(topic)
                .idempotencyKey("idempotency-1")
                .tenantId(tenantId)
                .workspaceId(workspaceId)
                .environmentId(environmentId)
                .ownershipScope(ownershipScope)
                .payload(payload)
                .build();
    }
}

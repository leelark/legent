package com.legent.platform.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.platform.service.GlobalSearchService;
import com.legent.platform.service.NotificationEngine;
import com.legent.platform.service.PlatformEventIdempotencyService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformEventConsumerTest {

    @Mock private WebhookDispatcherService webhookDispatcherService;
    @Mock private NotificationEngine notificationEngine;
    @Mock private GlobalSearchService searchService;
    @Mock private PlatformEventIdempotencyService idempotencyService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void consumeWebhookTriggers_whenDispatchAcceptanceFails_rethrowsAndClearsTenantContext() {
        PlatformEventConsumer consumer = consumer();
        allowClaim(AppConstants.TOPIC_WEBHOOK_TRIGGERED);
        doThrow(new IllegalStateException("retry store unavailable"))
                .when(webhookDispatcherService).dispatch(eq("t1"), eq("email.bounced"), any());

        EventEnvelope<String> event = platformEvent(
                AppConstants.TOPIC_WEBHOOK_TRIGGERED,
                "{\"eventToDispatch\":\"email.bounced\",\"data\":{\"id\":\"m1\"}}");

        assertThrows(IllegalStateException.class, () -> consumer.consumeWebhookTriggers(event));
        verify(idempotencyService).releaseClaim(
                eq("t1"), any(), eq(AppConstants.TOPIC_WEBHOOK_TRIGGERED), eq("event-1"), eq("idempotency-1"));
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void consumeWebhookTriggers_setsWorkspaceContextFromEnvelopeDuringDispatch() {
        PlatformEventConsumer consumer = consumer();
        allowClaim(AppConstants.TOPIC_WEBHOOK_TRIGGERED);
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

        verify(idempotencyService).markProcessed(
                "t1", "w1", AppConstants.TOPIC_WEBHOOK_TRIGGERED, "event-1", "idempotency-1");
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
        assertNull(TenantContext.getEnvironmentId());
    }

    @Test
    void consumeWebhookTriggers_duplicateClaimSkipsDispatch() {
        PlatformEventConsumer consumer = consumer();
        when(idempotencyService.claimIfNew(
                eq("t1"), any(), eq(AppConstants.TOPIC_WEBHOOK_TRIGGERED), eq("event-1"), eq("idempotency-1")))
                .thenReturn(false);

        EventEnvelope<String> event = platformEvent(
                AppConstants.TOPIC_WEBHOOK_TRIGGERED,
                "{\"eventToDispatch\":\"email.bounced\",\"data\":{\"id\":\"m1\"}}");

        consumer.consumeWebhookTriggers(event);

        verifyNoInteractions(webhookDispatcherService, notificationEngine, searchService);
        verify(idempotencyService, never()).markProcessed(any(), any(), any(), any(), any());
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void consumeWebhookTriggers_rejectsWorkspaceScopedEventWithoutWorkspaceId() {
        PlatformEventConsumer consumer = consumer();
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
    void consumeWebhookTriggers_rejectsMissingEventToDispatchBeforeDispatch() {
        PlatformEventConsumer consumer = consumer();
        EventEnvelope<String> event = platformEvent(
                AppConstants.TOPIC_WEBHOOK_TRIGGERED,
                "{\"eventToDispatch\":\" \",\"data\":{\"id\":\"m1\"}}");

        assertThrows(IllegalStateException.class, () -> consumer.consumeWebhookTriggers(event));

        verifyNoInteractions(webhookDispatcherService, notificationEngine, searchService);
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void consumeNotificationEvents_rejectsMissingTenantIdWithoutSideEffects() {
        PlatformEventConsumer consumer = consumer();
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
        PlatformEventConsumer consumer = consumer();
        EventEnvelope<String> event = platformEvent(
                AppConstants.TOPIC_NOTIFICATION_CREATED,
                "{\"ownershipScope\":\"WORKSPACE\",\"userId\":\"user-1\",\"title\":\"Title\",\"message\":\"Message\",\"severity\":\"INFO\",\"linkUrl\":\"/app\"}");

        assertThrows(IllegalStateException.class, () -> consumer.consumeNotificationEvents(event));

        verifyNoInteractions(webhookDispatcherService, notificationEngine, searchService);
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
    }

    @Test
    void consumeNotificationEvents_rejectsMissingRequiredFieldsBeforePersistence() {
        PlatformEventConsumer consumer = consumer();
        String[] payloads = {
                "{\"title\":\"Title\",\"message\":\"Message\"}",
                "{\"userId\":\"user-1\",\"message\":\"Message\"}",
                "{\"userId\":\"user-1\",\"title\":\"Title\"}"
        };

        for (String payload : payloads) {
            EventEnvelope<String> event = platformEvent(AppConstants.TOPIC_NOTIFICATION_CREATED, payload);

            assertThrows(IllegalStateException.class, () -> consumer.consumeNotificationEvents(event));
        }

        verifyNoInteractions(webhookDispatcherService, notificationEngine, searchService);
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
    }

    @Test
    void consumeNotificationEvents_setsWorkspaceContextDuringCreation() {
        PlatformEventConsumer consumer = consumer();
        allowClaim(AppConstants.TOPIC_NOTIFICATION_CREATED);
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

        verify(idempotencyService).markProcessed(
                "t1", "w1", AppConstants.TOPIC_NOTIFICATION_CREATED, "event-1", "idempotency-1");
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
    }

    @Test
    void consumeNotificationEvents_duplicateClaimSkipsNotification() {
        PlatformEventConsumer consumer = consumer();
        when(idempotencyService.claimIfNew(
                eq("t1"), any(), eq(AppConstants.TOPIC_NOTIFICATION_CREATED), eq("event-1"), eq("idempotency-1")))
                .thenReturn(false);

        EventEnvelope<String> event = platformEvent(
                AppConstants.TOPIC_NOTIFICATION_CREATED,
                "{\"userId\":\"user-1\",\"title\":\"Title\",\"message\":\"Message\",\"severity\":\"INFO\",\"linkUrl\":\"/app\"}");

        consumer.consumeNotificationEvents(event);

        verifyNoInteractions(webhookDispatcherService, notificationEngine, searchService);
        verify(idempotencyService, never()).markProcessed(any(), any(), any(), any(), any());
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void consumeNotificationEvents_whenMarkProcessedFailsAfterCreation_doesNotReleaseClaim() {
        PlatformEventConsumer consumer = consumer();
        allowClaim(AppConstants.TOPIC_NOTIFICATION_CREATED);
        doThrow(new IllegalStateException("idempotency update failed"))
                .when(idempotencyService).markProcessed(
                        eq("t1"), any(), eq(AppConstants.TOPIC_NOTIFICATION_CREATED), eq("event-1"), eq("idempotency-1"));

        EventEnvelope<String> event = platformEvent(
                AppConstants.TOPIC_NOTIFICATION_CREATED,
                "{\"userId\":\"user-1\",\"title\":\"Title\",\"message\":\"Message\",\"severity\":\"INFO\",\"linkUrl\":\"/app\"}");

        assertThrows(IllegalStateException.class, () -> consumer.consumeNotificationEvents(event));

        verify(notificationEngine).createNotification("t1", "user-1", "Title", "Message", "INFO", "/app");
        verify(idempotencyService, never()).releaseClaim(any(), any(), any(), any(), any());
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void consumeSearchIndexUpdates_whenIndexingFails_rethrowsAndClearsTenantContext() {
        PlatformEventConsumer consumer = consumer();
        allowClaim(AppConstants.TOPIC_SEARCH_INDEX_UPDATED);
        doThrow(new IllegalStateException("metadata serialization failed"))
                .when(searchService).indexDocument(eq("t1"), eq("CAMPAIGN"), eq("c1"), eq("Campaign"), eq("copy"), any());

        EventEnvelope<String> event = platformEvent(
                AppConstants.TOPIC_SEARCH_INDEX_UPDATED,
                "{\"entityType\":\"CAMPAIGN\",\"entityId\":\"c1\",\"title\":\"Campaign\",\"searchableText\":\"copy\",\"metadata\":{\"key\":\"value\"}}");

        assertThrows(IllegalStateException.class, () -> consumer.consumeSearchIndexUpdates(event));
        verify(idempotencyService).releaseClaim(
                eq("t1"), any(), eq(AppConstants.TOPIC_SEARCH_INDEX_UPDATED), eq("event-1"), eq("idempotency-1"));
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void consumeSearchIndexUpdates_rejectsMissingRequiredFieldsBeforeIndexing() {
        PlatformEventConsumer consumer = consumer();
        String[] payloads = {
                "{\"entityId\":\"c1\",\"title\":\"Campaign\",\"searchableText\":\"copy\"}",
                "{\"entityType\":\"CAMPAIGN\",\"title\":\"Campaign\",\"searchableText\":\"copy\"}",
                "{\"entityType\":\"CAMPAIGN\",\"entityId\":\"c1\",\"searchableText\":\"copy\"}",
                "{\"entityType\":\"CAMPAIGN\",\"entityId\":\"c1\",\"title\":\"Campaign\"}"
        };

        for (String payload : payloads) {
            EventEnvelope<String> event = platformEvent(AppConstants.TOPIC_SEARCH_INDEX_UPDATED, payload);

            assertThrows(IllegalStateException.class, () -> consumer.consumeSearchIndexUpdates(event));
        }

        verifyNoInteractions(webhookDispatcherService, notificationEngine, searchService);
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
    }

    @Test
    void consumeSearchIndexUpdates_setsWorkspaceContextDuringIndexing() {
        PlatformEventConsumer consumer = consumer();
        allowClaim(AppConstants.TOPIC_SEARCH_INDEX_UPDATED);
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

        verify(idempotencyService).markProcessed(
                "t1", "w1", AppConstants.TOPIC_SEARCH_INDEX_UPDATED, "event-1", "idempotency-1");
        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
    }

    @Test
    void consumeSearchIndexUpdates_duplicateClaimSkipsIndexing() {
        PlatformEventConsumer consumer = consumer();
        when(idempotencyService.claimIfNew(
                eq("t1"), any(), eq(AppConstants.TOPIC_SEARCH_INDEX_UPDATED), eq("event-1"), eq("idempotency-1")))
                .thenReturn(false);

        EventEnvelope<String> event = platformEvent(
                AppConstants.TOPIC_SEARCH_INDEX_UPDATED,
                "{\"entityType\":\"CAMPAIGN\",\"entityId\":\"c1\",\"title\":\"Campaign\",\"searchableText\":\"copy\",\"metadata\":{\"key\":\"value\"}}");

        consumer.consumeSearchIndexUpdates(event);

        verifyNoInteractions(webhookDispatcherService, notificationEngine, searchService);
        verify(idempotencyService, never()).markProcessed(any(), any(), any(), any(), any());
        assertNull(TenantContext.getTenantId());
    }

    private PlatformEventConsumer consumer() {
        return new PlatformEventConsumer(
                new ObjectMapper(), webhookDispatcherService, notificationEngine, searchService, idempotencyService);
    }

    private void allowClaim(String topic) {
        when(idempotencyService.claimIfNew(eq("t1"), any(), eq(topic), eq("event-1"), eq("idempotency-1")))
                .thenReturn(true);
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

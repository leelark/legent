package com.legent.platform.event;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

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

        EventEnvelope<String> event = EventEnvelope.<String>builder()
                .tenantId("t1")
                .payload("{\"eventToDispatch\":\"email.bounced\",\"data\":{\"id\":\"m1\"}}")
                .build();

        assertThrows(IllegalStateException.class, () -> consumer.consumeWebhookTriggers(event));
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void consumeSearchIndexUpdates_whenIndexingFails_rethrowsAndClearsTenantContext() {
        PlatformEventConsumer consumer = new PlatformEventConsumer(
                new ObjectMapper(), webhookDispatcherService, notificationEngine, searchService);
        doThrow(new IllegalStateException("metadata serialization failed"))
                .when(searchService).indexDocument(eq("t1"), eq("CAMPAIGN"), eq("c1"), eq("Campaign"), eq("copy"), any());

        EventEnvelope<String> event = EventEnvelope.<String>builder()
                .tenantId("t1")
                .payload("{\"entityType\":\"CAMPAIGN\",\"entityId\":\"c1\",\"title\":\"Campaign\",\"searchableText\":\"copy\",\"metadata\":{\"key\":\"value\"}}")
                .build();

        assertThrows(IllegalStateException.class, () -> consumer.consumeSearchIndexUpdates(event));
        assertNull(TenantContext.getTenantId());
    }
}

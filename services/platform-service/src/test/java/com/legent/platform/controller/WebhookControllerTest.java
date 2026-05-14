package com.legent.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.platform.domain.WebhookConfig;
import com.legent.platform.repository.WebhookConfigRepository;
import com.legent.security.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebhookControllerTest {

    private final WebhookConfigRepository repository = mock(WebhookConfigRepository.class);
    private final WebhookController controller = new WebhookController(repository);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void listWebhooksDoesNotSerializeSecretKey() throws Exception {
        TenantContext.setTenantId("tenant-1");
        WebhookConfig config = new WebhookConfig();
        config.setId("webhook-1");
        config.setTenantId("tenant-1");
        config.setName("Delivery events");
        config.setEndpointUrl("https://example.com/webhook");
        config.setEventsSubscribed("[\"email.sent\"]");
        config.setSecretKey("secret-value");
        config.setIsActive(true);
        when(repository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(config));

        String json = objectMapper.writeValueAsString(controller.listWebhooks().getData());

        assertThat(json).doesNotContain("secretKey");
        assertThat(json).doesNotContain("secret-value");
        assertThat(json).contains("\"secretConfigured\":true");
    }
}

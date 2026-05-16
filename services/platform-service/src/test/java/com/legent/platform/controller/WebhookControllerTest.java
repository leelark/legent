package com.legent.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.platform.domain.WebhookConfig;
import com.legent.platform.repository.WebhookConfigRepository;
import com.legent.security.TenantContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookControllerTest {

    private final WebhookConfigRepository repository = mock(WebhookConfigRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebhookController controller = new WebhookController(repository, objectMapper);

    @BeforeEach
    void setUpTenantContext() {
        TenantContext.setTenantId("tenant-1");
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void listWebhooksDoesNotSerializeSecretKey() throws Exception {
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

    @Test
    void createWebhookSavesTenantScopedValidatedWebhook() {
        WebhookConfig request = validWebhook();
        request.setEndpointUrl(" https://93.184.216.34/webhook ");
        request.setEventsSubscribed("[\"Email.Sent\", \" email.sent \", \"email.failed\"]");
        request.setSecretKey("  0123456789abcdefABCDEF!@#$%^&*()  ");
        when(repository.save(any(WebhookConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        controller.createWebhook(request);

        ArgumentCaptor<WebhookConfig> captor = ArgumentCaptor.forClass(WebhookConfig.class);
        verify(repository).save(captor.capture());
        WebhookConfig saved = captor.getValue();
        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getTenantId()).isEqualTo("tenant-1");
        assertThat(saved.getEndpointUrl()).isEqualTo("https://93.184.216.34/webhook");
        assertThat(saved.getEventsSubscribed()).isEqualTo("[\"email.sent\",\"email.failed\"]");
        assertThat(saved.getSecretKey()).isEqualTo("0123456789abcdefABCDEF!@#$%^&*()");
    }

    @Test
    void createWebhookRejectsNonHttpsEndpoint() {
        WebhookConfig request = validWebhook();
        request.setEndpointUrl("http://93.184.216.34/webhook");

        assertBadRequest(() -> controller.createWebhook(request), "must use https");
        verify(repository, never()).save(any());
    }

    @Test
    void createWebhookRejectsEmptyEventList() {
        WebhookConfig request = validWebhook();
        request.setEventsSubscribed("[\"\", \"  \"]");

        assertBadRequest(() -> controller.createWebhook(request), "at least one event");
        verify(repository, never()).save(any());
    }

    @Test
    void createWebhookRejectsMissingSecret() {
        WebhookConfig request = validWebhook();
        request.setSecretKey(" ");

        assertBadRequest(() -> controller.createWebhook(request), "secretKey is required");
        verify(repository, never()).save(any());
    }

    @Test
    void createWebhookRejectsWeakSecret() {
        WebhookConfig request = validWebhook();
        request.setSecretKey("client-supplied-secret");

        assertBadRequest(() -> controller.createWebhook(request), "at least 32 characters");
        verify(repository, never()).save(any());
    }

    @Test
    void createWebhookRejectsUnsupportedEvent() {
        WebhookConfig request = validWebhook();
        request.setEventsSubscribed("[\"delivery.failed\"]");

        assertBadRequest(() -> controller.createWebhook(request), "unsupported event name: delivery.failed");
        verify(repository, never()).save(any());
    }

    @Test
    void createWebhookRejectsMalformedEvent() {
        WebhookConfig request = validWebhook();
        request.setEventsSubscribed("[\"email.sent;curl\"]");

        assertBadRequest(() -> controller.createWebhook(request), "malformed event name");
        verify(repository, never()).save(any());
    }

    @Test
    void createWebhookRejectsExcessiveEvents() {
        WebhookConfig request = validWebhook();
        StringBuilder events = new StringBuilder();
        for (int i = 0; i < 51; i++) {
            if (i > 0) {
                events.append(',');
            }
            events.append("email.event").append(i);
        }
        request.setEventsSubscribed(events.toString());

        assertBadRequest(() -> controller.createWebhook(request), "more than 50 events");
        verify(repository, never()).save(any());
    }

    private WebhookConfig validWebhook() {
        WebhookConfig config = new WebhookConfig();
        config.setName("Delivery events");
        config.setEndpointUrl("https://93.184.216.34/webhook");
        config.setEventsSubscribed("[\"email.sent\"]");
        config.setSecretKey("0123456789abcdefABCDEF!@#$%^&*()");
        config.setIsActive(true);
        return config;
    }

    private void assertBadRequest(Runnable action, String reasonFragment) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getReason()).contains(reasonFragment);
                });
    }
}

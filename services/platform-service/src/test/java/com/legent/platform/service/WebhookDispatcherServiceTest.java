package com.legent.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.platform.domain.WebhookConfig;
import com.legent.platform.repository.WebhookConfigRepository;
import com.legent.platform.repository.WebhookLogRepository;
import com.legent.platform.repository.WebhookRetryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class WebhookDispatcherServiceTest {

    @Mock private WebhookConfigRepository configRepository;
    @Mock private WebhookLogRepository logRepository;
    @Mock private WebhookRetryRepository retryRepository;
    @Mock private WebClient webClient;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;

    private WebhookDispatcherService service;

    @BeforeEach
    void setUp() {
        service = new WebhookDispatcherService(configRepository, logRepository, retryRepository, webClient, new ObjectMapper());
    }

    @Test
    void dispatch_whenJsonArrayHasExactEvent_dispatches() {
        WebhookConfig config = config("hk1", "[\"email.bounced\",\"workflow.completed\"]");
        when(configRepository.findByTenantIdAndIsActiveTrue("t1")).thenReturn(List.of(config));
        mockPostChain();

        service.dispatch("t1", "email.bounced", Map.of("test", "data"));

        verify(webClient).post();
    }

    @Test
    void dispatch_whenEventOnlyPartiallyMatches_doesNotDispatch() {
        WebhookConfig config = config("hk1", "[\"email.bounced\"]");
        when(configRepository.findByTenantIdAndIsActiveTrue("t1")).thenReturn(List.of(config));

        service.dispatch("t1", "email.bounce", Map.of("test", "data"));

        verify(webClient, never()).post();
    }

    @Test
    void dispatch_whenCommaSeparatedSubscriptionMatches_dispatches() {
        WebhookConfig config = config("hk1", "email.sent, email.failed");
        when(configRepository.findByTenantIdAndIsActiveTrue("t1")).thenReturn(List.of(config));
        mockPostChain();

        service.dispatch("t1", "EMAIL.SENT", Map.of("test", "data"));

        verify(webClient).post();
    }

    @Test
    void dispatch_whenSubscriptionPayloadMalformed_doesNotDispatch() {
        WebhookConfig config = config("hk1", "[invalid-json");
        when(configRepository.findByTenantIdAndIsActiveTrue("t1")).thenReturn(List.of(config));

        service.dispatch("t1", "email.bounced", Map.of("test", "data"));

        verify(webClient, never()).post();
    }

    @Test
    void dispatch_whenDeliveryFails_persistsRetryBeforeReturning() {
        WebhookConfig config = config("hk1", "[\"email.bounced\"]");
        when(configRepository.findByTenantIdAndIsActiveTrue("t1")).thenReturn(List.of(config));
        mockPostChainWithError();

        assertDoesNotThrow(() -> service.dispatch("t1", "email.bounced", Map.of("test", "data")));

        verify(retryRepository).save(any());
    }

    @Test
    void dispatch_whenRetryPersistenceFails_throws() {
        WebhookConfig config = config("hk1", "[\"email.bounced\"]");
        when(configRepository.findByTenantIdAndIsActiveTrue("t1")).thenReturn(List.of(config));
        doThrow(new IllegalStateException("database unavailable")).when(retryRepository).save(any());
        mockPostChainWithError();

        assertThrows(IllegalStateException.class,
                () -> service.dispatch("t1", "email.bounced", Map.of("test", "data")));
    }

    private void mockPostChain() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(URI.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.exchangeToMono(any())).thenReturn(Mono.just(true));
    }

    private void mockPostChainWithError() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(URI.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.exchangeToMono(any())).thenReturn(Mono.error(new IllegalArgumentException("delivery failed")));
    }

    private WebhookConfig config(String id, String eventsSubscribed) {
        WebhookConfig config = new WebhookConfig();
        config.setId(id);
        config.setTenantId("t1");
        config.setEventsSubscribed(eventsSubscribed);
        config.setEndpointUrl("https://example.com/webhook");
        config.setSecretKey("test-webhook-secret");
        return config;
    }
}

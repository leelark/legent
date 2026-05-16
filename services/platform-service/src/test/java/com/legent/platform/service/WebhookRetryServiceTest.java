package com.legent.platform.service;

import com.legent.platform.domain.WebhookRetry;
import com.legent.platform.repository.WebhookConfigRepository;
import com.legent.platform.repository.WebhookLogRepository;
import com.legent.platform.repository.WebhookRetryRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookRetryServiceTest {

    @Mock private WebhookRetryRepository retryRepository;
    @Mock private WebhookConfigRepository configRepository;
    @Mock private WebhookLogRepository logRepository;
    @Mock private WebClient webClient;

    private WebhookRetryService service;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        service = new WebhookRetryService(retryRepository, configRepository, logRepository, webClient, directExecutor);
    }

    @Test
    void processRetryLooksUpWebhookConfigByIdAndTenant() {
        WebhookRetry retry = retry("retry-1", "tenant-a", "webhook-1");
        when(configRepository.findByIdAndTenantId("webhook-1", "tenant-a")).thenReturn(Optional.empty());

        service.processRetry(retry);

        verify(configRepository).findByIdAndTenantId("webhook-1", "tenant-a");
        verify(configRepository, never()).findById("webhook-1");
        verify(retryRepository, times(2)).save(retry);
        verifyNoInteractions(webClient, logRepository);
        assertThat(retry.getStatus()).isEqualTo("FAILED");
        assertThat(retry.getLastError()).contains("Webhook configuration not found or inactive");
    }

    private WebhookRetry retry(String id, String tenantId, String webhookId) {
        WebhookRetry retry = new WebhookRetry();
        retry.setId(id);
        retry.setTenantId(tenantId);
        retry.setWebhookId(webhookId);
        retry.setEventType("email.bounced");
        retry.setPayload("{\"id\":\"message-1\"}");
        retry.setRetryCount(0);
        retry.setMaxRetries(3);
        retry.setStatus("PENDING");
        retry.setCreatedAt(Instant.now());
        retry.setNextRetryAt(Instant.now());
        return retry;
    }
}

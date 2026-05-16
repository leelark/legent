package com.legent.platform.service;

import com.legent.platform.config.WebhookWebClient;
import com.legent.platform.domain.WebhookRetry;
import com.legent.platform.repository.WebhookConfigRepository;
import com.legent.platform.repository.WebhookLogRepository;
import com.legent.platform.repository.WebhookRetryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        service = new WebhookRetryService(
                retryRepository,
                configRepository,
                logRepository,
                new WebhookWebClient(webClient),
                directExecutor);
    }

    @Test
    void processRetryLooksUpWebhookConfigByIdTenantAndWorkspace() {
        WebhookRetry retry = retry("retry-1", "tenant-a", "webhook-1");
        when(configRepository.findByIdAndTenantIdAndWorkspaceId("webhook-1", "tenant-a", "workspace-a"))
                .thenReturn(Optional.empty());

        service.processRetry(retry);

        verify(configRepository).findByIdAndTenantIdAndWorkspaceId("webhook-1", "tenant-a", "workspace-a");
        verify(configRepository, never()).findById("webhook-1");
        verify(configRepository, never()).findByIdAndTenantIdAndWorkspaceIdIsNull("webhook-1", "tenant-a");
        verify(retryRepository, times(2)).save(retry);
        verifyNoInteractions(webClient, logRepository);
        assertThat(retry.getStatus()).isEqualTo("FAILED");
        assertThat(retry.getLastError()).contains("Webhook configuration not found or inactive");
    }

    @Test
    void processRetryWithoutWorkspaceLooksUpTenantGlobalWebhookConfig() {
        WebhookRetry retry = retry("retry-1", "tenant-a", "webhook-1");
        retry.setWorkspaceId(null);
        when(configRepository.findByIdAndTenantIdAndWorkspaceIdIsNull("webhook-1", "tenant-a"))
                .thenReturn(Optional.empty());

        service.processRetry(retry);

        verify(configRepository).findByIdAndTenantIdAndWorkspaceIdIsNull("webhook-1", "tenant-a");
        verify(configRepository, never()).findByIdAndTenantIdAndWorkspaceId(any(), any(), any());
        assertThat(retry.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void processRetriesRequestsBoundedPageOfPendingRetries() {
        when(retryRepository.findPendingRetries(any(Instant.class), any(Pageable.class))).thenReturn(List.of());

        service.processRetries();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(retryRepository).findPendingRetries(any(Instant.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(50);
    }

    @Test
    void processRetriesReleasesBoundedStaleRetryingClaimsBeforePendingScan() {
        WebhookRetry retry = retry("retry-1", "tenant-a", "webhook-1");
        retry.setStatus("RETRYING");
        retry.setClaimStartedAt(Instant.now().minusSeconds(180));
        when(retryRepository.findStaleRetryingRecords(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(retry));
        when(retryRepository.releaseStaleRetryingRecord(
                eq("retry-1"), any(Instant.class), any(Instant.class), any(String.class)))
                .thenReturn(1);
        when(retryRepository.findPendingRetries(any(Instant.class), any(Pageable.class))).thenReturn(List.of());

        service.processRetries();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(retryRepository).findStaleRetryingRecords(any(Instant.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
        verify(retryRepository).releaseStaleRetryingRecord(
                eq("retry-1"), any(Instant.class), any(Instant.class), any(String.class));
        verify(retryRepository).findPendingRetries(any(Instant.class), any(Pageable.class));
        verifyNoInteractions(configRepository, webClient, logRepository);
    }

    @Test
    void processRetriesClaimsPendingRetryBeforeProcessing() {
        WebhookRetry retry = retry("retry-1", "tenant-a", "webhook-1");
        when(retryRepository.findPendingRetries(any(Instant.class), any(Pageable.class))).thenReturn(List.of(retry));
        when(retryRepository.claimPendingRetry(eq("retry-1"), any(Instant.class), any(Instant.class))).thenReturn(1);
        when(configRepository.findByIdAndTenantIdAndWorkspaceId("webhook-1", "tenant-a", "workspace-a"))
                .thenReturn(Optional.empty());

        service.processRetries();

        verify(retryRepository).claimPendingRetry(eq("retry-1"), any(Instant.class), any(Instant.class));
        verify(configRepository).findByIdAndTenantIdAndWorkspaceId("webhook-1", "tenant-a", "workspace-a");
        assertThat(retry.getStatus()).isEqualTo("FAILED");
        assertThat(retry.getLastError()).contains("Webhook configuration not found or inactive");
        assertThat(retry.getClaimStartedAt()).isNull();
    }

    @Test
    void processRetriesSkipsRetryClaimedByAnotherWorker() {
        WebhookRetry retry = retry("retry-1", "tenant-a", "webhook-1");
        when(retryRepository.findPendingRetries(any(Instant.class), any(Pageable.class))).thenReturn(List.of(retry));
        when(retryRepository.claimPendingRetry(eq("retry-1"), any(Instant.class), any(Instant.class))).thenReturn(0);

        service.processRetries();

        verify(retryRepository).claimPendingRetry(eq("retry-1"), any(Instant.class), any(Instant.class));
        verify(retryRepository, never()).save(retry);
        verifyNoInteractions(configRepository, webClient, logRepository);
        assertThat(retry.getStatus()).isEqualTo("PENDING");
    }

    private WebhookRetry retry(String id, String tenantId, String webhookId) {
        WebhookRetry retry = new WebhookRetry();
        retry.setId(id);
        retry.setTenantId(tenantId);
        retry.setWorkspaceId("workspace-a");
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

package com.legent.audience.service;

import com.legent.audience.domain.DoubleOptInToken;
import com.legent.audience.event.AudienceEventPublisher;
import com.legent.audience.repository.ConsentRecordRepository;
import com.legent.audience.repository.DoubleOptInTokenRepository;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    @Mock
    private ConsentRecordRepository consentRepository;
    @Mock
    private DoubleOptInTokenRepository tokenRepository;
    @Mock
    private SubscriberRepository subscriberRepository;
    @Mock
    private AudienceEventPublisher eventPublisher;

    private ConsentService service;

    @BeforeEach
    void setUp() {
        service = new ConsentService(consentRepository, tokenRepository, subscriberRepository, eventPublisher);
        ReflectionTestUtils.setField(service, "doubleOptInExpiryHours", 48);
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createDoubleOptInTokenWaitsForPublishAndReturnsSavedToken() {
        when(tokenRepository.findPendingTokenForSubscriber("tenant-1", "workspace-1", "subscriber-1"))
                .thenReturn(Optional.empty());
        when(tokenRepository.save(any(DoubleOptInToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventPublisher.publishDoubleOptInRequested(eq("tenant-1"), eq("subscriber-1"), eq("user@example.test"), any()))
                .thenReturn(CompletableFuture.<SendResult<String, Object>>completedFuture(null));

        DoubleOptInToken saved = service.createDoubleOptInToken(
                "tenant-1", "subscriber-1", "user@example.test", "127.0.0.1", "JUnit");

        assertThat(saved.getTenantId()).isEqualTo("tenant-1");
        assertThat(saved.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(saved.getStatus()).isEqualTo(DoubleOptInToken.TokenStatus.PENDING);
        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher).publishDoubleOptInRequested(
                eq("tenant-1"), eq("subscriber-1"), eq("user@example.test"), rawTokenCaptor.capture());
        assertThat(rawTokenCaptor.getValue()).isNotBlank();
    }

    @Test
    void createDoubleOptInTokenSurfacesPublishFailure() {
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("kafka unavailable"));
        when(tokenRepository.findPendingTokenForSubscriber("tenant-1", "workspace-1", "subscriber-1"))
                .thenReturn(Optional.empty());
        when(tokenRepository.save(any(DoubleOptInToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventPublisher.publishDoubleOptInRequested(eq("tenant-1"), eq("subscriber-1"), eq("user@example.test"), any()))
                .thenReturn(failed);

        assertThatThrownBy(() -> service.createDoubleOptInToken(
                "tenant-1", "subscriber-1", "user@example.test", "127.0.0.1", "JUnit"))
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("kafka unavailable");
    }

    @Test
    void createDoubleOptInTokenReusesExistingPendingTokenWithoutRepublishing() {
        DoubleOptInToken existing = new DoubleOptInToken();
        existing.setTenantId("tenant-1");
        existing.setWorkspaceId("workspace-1");
        existing.setSubscriberId("subscriber-1");
        existing.setEmail("user@example.test");
        existing.setStatus(DoubleOptInToken.TokenStatus.PENDING);
        existing.setExpiresAt(Instant.now().plusSeconds(3600));
        when(tokenRepository.findPendingTokenForSubscriber("tenant-1", "workspace-1", "subscriber-1"))
                .thenReturn(Optional.of(existing));

        DoubleOptInToken returned = service.createDoubleOptInToken(
                "tenant-1", "subscriber-1", "user@example.test", "127.0.0.1", "JUnit");

        assertThat(returned).isSameAs(existing);
        verify(eventPublisher, never()).publishDoubleOptInRequested(any(), any(), any(), any());
    }
}

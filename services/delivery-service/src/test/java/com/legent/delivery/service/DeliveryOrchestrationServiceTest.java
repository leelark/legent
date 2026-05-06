package com.legent.delivery.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.cache.service.CacheService;
import com.legent.delivery.adapter.ProviderDispatchException;
import com.legent.delivery.adapter.impl.MockProviderAdapter;
import com.legent.delivery.client.ContentServiceClient;
import com.legent.delivery.domain.MessageLog;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.domain.WarmupState;
import com.legent.delivery.event.DeliveryEventPublisher;
import com.legent.delivery.repository.MessageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)

class DeliveryOrchestrationServiceTest {

    @Mock private ProviderSelectionStrategy providerStrategy;
    @Mock private MessageLogRepository messageLogRepository;
    @Mock private DeliveryEventPublisher eventPublisher;
    @Mock private ContentProcessingService contentProcessingService;
    @Mock private CacheService cacheService;
    @Mock private ContentServiceClient contentServiceClient;
    @Mock private ObjectMapper objectMapper;
    @Mock private InboxSafetyService inboxSafetyService;
    @Mock private SendRateControlService sendRateControlService;
    @Mock private WarmupService warmupService;
    @Mock private RetryPolicyService retryPolicyService;

    @InjectMocks private DeliveryOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        InboxSafetyService.InboxSafetyResult allow = new InboxSafetyService.InboxSafetyResult(
                InboxSafetyService.SafetyDecision.ALLOW, 5, 1000, 1000, List.of(), List.of(), "eval-1");
        WarmupState warmup = new WarmupState();
        warmup.setStage("NEW");
        lenient().when(inboxSafetyService.evaluate(any())).thenReturn(allow);
        lenient().when(sendRateControlService.reserve(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(new SendRateControlService.RateLimitDecision(true, "key", 1, null, "ok", null));
        lenient().when(warmupService.getOrCreate(anyString(), anyString(), anyString(), anyString())).thenReturn(warmup);
        lenient().when(retryPolicyService.classify(any())).thenReturn("TRANSIENT");
    }

    @Test
    void processSendRequest_Success() throws Exception {
        Map<String, Object> payload = Map.of(
                "email", "test@example.com",
                "subscriberId", "sub-1",
                "campaignId", "camp-1",
                "workspaceId", "workspace-1",
                "htmlContent", "Hello",
                "subject", "Hi"
        );
        
        MockProviderAdapter mockAdapter = mock(MockProviderAdapter.class);
        SmtpProvider mockProvider = new SmtpProvider();
        mockProvider.setId("prov-1");
        ProviderSelectionStrategy.ProviderSelectionResult result = new ProviderSelectionStrategy.ProviderSelectionResult(mockAdapter, mockProvider);
        
        when(messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId(any(), any(), any())).thenReturn(Optional.empty());
        when(messageLogRepository.save(any(MessageLog.class))).thenAnswer(invocation -> invocation.getArgument(0, MessageLog.class));
        when(messageLogRepository.saveAndFlush(any(MessageLog.class))).thenAnswer(invocation -> {
            MessageLog log = invocation.getArgument(0, MessageLog.class);
            log.setId("log-1");
            return log;
        });
        when(messageLogRepository.claimForProcessing(nullable(String.class), eq(MessageLog.DeliveryStatus.PENDING.name()), eq(MessageLog.DeliveryStatus.PROCESSING.name()))).thenReturn(1);
        when(providerStrategy.selectProvider("tenant-1", "example.com")).thenReturn(result);
        when(contentProcessingService.processContent(any(), any(), any(), any(), any(), any())).thenReturn("Processed HTML");

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(mockAdapter).sendEmail(eq("test@example.com"), anyString(), anyString(), anyMap(), eq(mockProvider));
        verify(eventPublisher).publishEmailSent(eq("tenant-1"), anyString(), eq("evt-123"), eq("camp-1"), any(), any(), eq("sub-1"), anyMap());
    }

    @Test
    void processSendRequest_PermanentFailure() throws Exception {
        Map<String, Object> payload = Map.of(
                "email", "bounce@example.com",
                "workspaceId", "workspace-1",
                "htmlContent", "Hello",
                "subject", "Hi"
        );
        
        SmtpProvider mockProvider = new SmtpProvider();
        mockProvider.setId("prov-1");
        MockProviderAdapter mockAdapter = mock(MockProviderAdapter.class);
        doThrow(new ProviderDispatchException("Hard bounce", true)).when(mockAdapter).sendEmail(any(), any(), any(), any(), eq(mockProvider));
        
        ProviderSelectionStrategy.ProviderSelectionResult result = new ProviderSelectionStrategy.ProviderSelectionResult(mockAdapter, mockProvider);
        
        when(messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId(any(), any(), any())).thenReturn(Optional.empty());
        when(messageLogRepository.save(any(MessageLog.class))).thenAnswer(invocation -> invocation.getArgument(0, MessageLog.class));
        when(messageLogRepository.saveAndFlush(any(MessageLog.class))).thenAnswer(invocation -> {
            MessageLog log = invocation.getArgument(0, MessageLog.class);
            log.setId("log-1");
            return log;
        });
        when(messageLogRepository.claimForProcessing(nullable(String.class), eq(MessageLog.DeliveryStatus.PENDING.name()), eq(MessageLog.DeliveryStatus.PROCESSING.name()))).thenReturn(1);
        when(providerStrategy.selectProvider("tenant-1", "example.com")).thenReturn(result);
        when(contentProcessingService.processContent(any(), any(), any(), any(), any(), any())).thenReturn("Processed HTML");
        when(retryPolicyService.decide(any(), anyInt()))
                .thenReturn(new RetryPolicyService.RetryDecision(false, "HARD_BOUNCE", null, "permanent"));

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(eventPublisher).publishEmailFailed(eq("tenant-1"), anyString(), eq("evt-123"), any(), any(), any(), any(), anyString(), anyMap());
        verify(eventPublisher).publishEmailBounced(eq("tenant-1"), eq("workspace-1"), eq("bounce@example.com"), anyString(), eq("example.com"), anyMap());
    }

    @Test
    void processSendRequest_TransientFailure_SchedulesRetry() throws Exception {
        Map<String, Object> payload = Map.of(
                "email", "delay@example.com",
                "workspaceId", "workspace-1",
                "htmlContent", "Hello",
                "subject", "Hi"
        );
        
        SmtpProvider mockProvider = new SmtpProvider();
        mockProvider.setId("prov-1");
        MockProviderAdapter mockAdapter = mock(MockProviderAdapter.class);
        doThrow(new ProviderDispatchException("Timeout", false)).when(mockAdapter).sendEmail(any(), any(), any(), any(), eq(mockProvider));
        
        ProviderSelectionStrategy.ProviderSelectionResult result = new ProviderSelectionStrategy.ProviderSelectionResult(mockAdapter, mockProvider);
        
        when(messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId(any(), any(), any())).thenReturn(Optional.empty());
        when(messageLogRepository.save(any(MessageLog.class))).thenAnswer(invocation -> invocation.getArgument(0, MessageLog.class));
        when(messageLogRepository.saveAndFlush(any(MessageLog.class))).thenAnswer(invocation -> {
            MessageLog log = invocation.getArgument(0, MessageLog.class);
            log.setId("log-1");
            return log;
        });
        when(messageLogRepository.claimForProcessing(nullable(String.class), eq(MessageLog.DeliveryStatus.PENDING.name()), eq(MessageLog.DeliveryStatus.PROCESSING.name()))).thenReturn(1);
        when(providerStrategy.selectProvider("tenant-1", "example.com")).thenReturn(result);
        when(contentProcessingService.processContent(any(), any(), any(), any(), any(), any())).thenReturn("Processed HTML");
        when(retryPolicyService.decide(any(), anyInt()))
                .thenReturn(new RetryPolicyService.RetryDecision(true, "NETWORK", Instant.now().plusSeconds(60), "retry"));

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(eventPublisher).publishRetryScheduled(eq("tenant-1"), eq("workspace-1"), eq("evt-123"), eq(1L), anyString(), anyMap());
    }

    @Test
    void processSendRequest_InvalidEmail_DoesNotSelectProviderAndMarksFailed() {
        Map<String, Object> payload = Map.of(
                "email", "invalid-email",
                "workspaceId", "workspace-1"
        );

        when(messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId("tenant-1", "workspace-1", "evt-123")).thenReturn(Optional.empty());
        when(messageLogRepository.save(any(MessageLog.class))).thenAnswer(invocation -> invocation.getArgument(0, MessageLog.class));
        when(messageLogRepository.saveAndFlush(any(MessageLog.class))).thenAnswer(invocation -> {
            MessageLog log = invocation.getArgument(0, MessageLog.class);
            log.setId("log-1");
            return log;
        });
        when(messageLogRepository.claimForProcessing(nullable(String.class), eq(MessageLog.DeliveryStatus.PENDING.name()), eq(MessageLog.DeliveryStatus.PROCESSING.name()))).thenReturn(1);
        when(retryPolicyService.decide(any(), anyInt()))
                .thenReturn(new RetryPolicyService.RetryDecision(false, "HARD_BOUNCE", null, "invalid"));

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(providerStrategy, never()).selectProvider(anyString(), anyString());
        verify(eventPublisher).publishEmailFailed(eq("tenant-1"), anyString(), eq("evt-123"), any(), any(), any(), any(), anyString(), anyMap());
        verify(eventPublisher).publishEmailBounced(eq("tenant-1"), eq("workspace-1"), eq("invalid-email"), anyString(), isNull(), anyMap());
    }

    @Test
    void processSendRequest_AlreadyProcessedMessage_SkipsDispatch() {
        MessageLog messageLog = new MessageLog();
        messageLog.setStatus(MessageLog.DeliveryStatus.SENT.name());

        when(messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId("tenant-1", "workspace-1", "evt-123")).thenReturn(Optional.of(messageLog));

        orchestrationService.processSendRequest(Map.of("email", "test@example.com", "workspaceId", "workspace-1"), "tenant-1", "evt-123");

        verify(providerStrategy, never()).selectProvider(anyString(), anyString());
        verify(eventPublisher, never()).publishEmailSent(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyMap());
        verify(messageLogRepository, never()).save(any(MessageLog.class));
    }
}

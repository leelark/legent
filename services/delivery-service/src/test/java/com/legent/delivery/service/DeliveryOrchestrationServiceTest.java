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
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        when(contentProcessingService.processContent(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn("Processed HTML");

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(mockAdapter).sendEmail(eq("test@example.com"), anyString(), anyString(), anyMap(), eq(mockProvider));
        verify(eventPublisher).publishEmailSent(eq("tenant-1"), anyString(), eq("evt-123"), eq("camp-1"), any(), any(), eq("sub-1"), anyMap());
    }

    @Test
    void processSendRequest_PersistsPayloadContentReference() throws Exception {
        Map<String, Object> payload = Map.of(
                "email", "test@example.com",
                "subscriberId", "sub-1",
                "campaignId", "camp-1",
                "workspaceId", "workspace-1",
                "contentReference", "  cr_1234567890abcdef1234567890abcdef  ",
                "htmlContent", "Hello",
                "subject", "Hi"
        );

        MockProviderAdapter mockAdapter = mock(MockProviderAdapter.class);
        SmtpProvider mockProvider = new SmtpProvider();
        mockProvider.setId("prov-1");
        ProviderSelectionStrategy.ProviderSelectionResult result = new ProviderSelectionStrategy.ProviderSelectionResult(mockAdapter, mockProvider);

        when(messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId(any(), any(), any())).thenReturn(Optional.empty());
        when(messageLogRepository.saveAndFlush(any(MessageLog.class))).thenAnswer(invocation -> {
            MessageLog log = invocation.getArgument(0, MessageLog.class);
            log.setId("log-1");
            return log;
        });
        when(messageLogRepository.claimForProcessing(nullable(String.class), eq(MessageLog.DeliveryStatus.PENDING.name()), eq(MessageLog.DeliveryStatus.PROCESSING.name()))).thenReturn(1);
        when(providerStrategy.selectProvider("tenant-1", "example.com")).thenReturn(result);
        when(contentProcessingService.processContent(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn("Processed HTML");

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        ArgumentCaptor<MessageLog> logCaptor = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogRepository).saveAndFlush(logCaptor.capture());
        assertEquals("cr_1234567890abcdef1234567890abcdef", logCaptor.getValue().getContentReference());
    }

    @Test
    void processScheduledRetries_ResolvesCrReferenceFromRedisContent() throws Exception {
        MessageLog retry = retryLog("cr_1234567890abcdef1234567890abcdef");
        DeliveryOrchestrationService self = mock(DeliveryOrchestrationService.class);
        ReflectionTestUtils.setField(orchestrationService, "self", self);

        Map<String, String> cachedContent = Map.of(
                "tenantId", "tenant-1",
                "workspaceId", "workspace-1",
                "campaignId", "camp-1",
                "messageId", "msg-1",
                "subject", "Cached subject",
                "htmlBody", "<p>Cached body</p>"
        );
        when(messageLogRepository.findEligibleForRetry(any())).thenReturn(List.of(retry));
        when(cacheService.get("email:content:cr_1234567890abcdef1234567890abcdef", String.class))
                .thenReturn(Optional.of("{\"subject\":\"Cached subject\"}"));
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(cachedContent);

        orchestrationService.processScheduledRetries();

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(self).processSendRequest(payloadCaptor.capture(), eq("tenant-1"), eq("msg-1"));
        assertEquals("Cached subject", payloadCaptor.getValue().get("subject"));
        assertEquals("<p>Cached body</p>", payloadCaptor.getValue().get("htmlBody"));
        assertEquals("cr_1234567890abcdef1234567890abcdef", payloadCaptor.getValue().get("contentReference"));
        verify(contentServiceClient, never()).fetchCampaignContent(anyString());
    }

    @Test
    void processScheduledRetries_MissingCrReferenceDoesNotSendPlaceholderContent() {
        MessageLog retry = retryLog("cr_missing");
        DeliveryOrchestrationService self = mock(DeliveryOrchestrationService.class);
        ReflectionTestUtils.setField(orchestrationService, "self", self);

        when(messageLogRepository.findEligibleForRetry(any())).thenReturn(List.of(retry));
        when(cacheService.get("email:content:cr_missing", String.class)).thenReturn(Optional.empty());

        orchestrationService.processScheduledRetries();

        verify(self, never()).processSendRequest(anyMap(), anyString(), anyString());
        verify(contentServiceClient, never()).fetchCampaignContent(anyString());
        ArgumentCaptor<MessageLog> logCaptor = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogRepository).save(logCaptor.capture());
        assertEquals(MessageLog.DeliveryStatus.FAILED.name(), logCaptor.getValue().getStatus());
        assertEquals("CONTENT_UNAVAILABLE", logCaptor.getValue().getFailureClass());
        verify(eventPublisher).publishEmailFailed(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("msg-1"),
                eq("camp-1"),
                eq("job-1"),
                eq("batch-1"),
                eq("sub-1"),
                contains("Rendered content reference is unavailable"),
                anyMap());
    }

    @Test
    void processScheduledRetries_NonCrContentMissDoesNotSendPlaceholderContent() {
        MessageLog retry = retryLog("ref:camp-1:msg-1");
        DeliveryOrchestrationService self = mock(DeliveryOrchestrationService.class);
        ReflectionTestUtils.setField(orchestrationService, "self", self);

        when(messageLogRepository.findEligibleForRetry(any())).thenReturn(List.of(retry));
        when(cacheService.get("email:content:ref:camp-1:msg-1", String.class)).thenReturn(Optional.empty());
        when(contentServiceClient.fetchCampaignContent("camp-1")).thenReturn(Map.of());

        orchestrationService.processScheduledRetries();

        verify(self, never()).processSendRequest(anyMap(), anyString(), anyString());
        ArgumentCaptor<MessageLog> logCaptor = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogRepository).save(logCaptor.capture());
        assertEquals(MessageLog.DeliveryStatus.FAILED.name(), logCaptor.getValue().getStatus());
        assertEquals("CONTENT_UNAVAILABLE", logCaptor.getValue().getFailureClass());
        verify(eventPublisher).publishEmailFailed(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("msg-1"),
                eq("camp-1"),
                eq("job-1"),
                eq("batch-1"),
                eq("sub-1"),
                contains("subject/htmlBody"),
                anyMap());
    }

    @Test
    void processScheduledRetries_IncompleteNonCrContentDoesNotSendPlaceholderContent() {
        MessageLog retry = retryLog("ref:camp-1:msg-1");
        DeliveryOrchestrationService self = mock(DeliveryOrchestrationService.class);
        ReflectionTestUtils.setField(orchestrationService, "self", self);

        when(messageLogRepository.findEligibleForRetry(any())).thenReturn(List.of(retry));
        when(cacheService.get("email:content:ref:camp-1:msg-1", String.class)).thenReturn(Optional.empty());
        when(contentServiceClient.fetchCampaignContent("camp-1")).thenReturn(Map.of("subject", "Recovered subject"));

        orchestrationService.processScheduledRetries();

        verify(self, never()).processSendRequest(anyMap(), anyString(), anyString());
        ArgumentCaptor<MessageLog> logCaptor = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogRepository).save(logCaptor.capture());
        assertEquals(MessageLog.DeliveryStatus.FAILED.name(), logCaptor.getValue().getStatus());
        assertEquals("CONTENT_UNAVAILABLE", logCaptor.getValue().getFailureClass());
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
        when(contentProcessingService.processContent(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn("Processed HTML");
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
        when(contentProcessingService.processContent(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn("Processed HTML");
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

    private MessageLog retryLog(String contentReference) {
        MessageLog log = new MessageLog();
        log.setId("log-1");
        log.setTenantId("tenant-1");
        log.setWorkspaceId("workspace-1");
        log.setCampaignId("camp-1");
        log.setJobId("job-1");
        log.setBatchId("batch-1");
        log.setMessageId("msg-1");
        log.setSubscriberId("sub-1");
        log.setEmail("test@example.com");
        log.setFromEmail("sender@example.com");
        log.setContentReference(contentReference);
        return log;
    }
}

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
import com.legent.delivery.repository.MessageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)

class DeliveryOrchestrationServiceTest {

    @Mock private ProviderSelectionStrategy providerStrategy;
    @Mock private MessageLogRepository messageLogRepository;
    @Mock private DeliveryFeedbackOutboxService feedbackOutboxService;
    @Mock private ContentProcessingService contentProcessingService;
    @Mock private CacheService cacheService;
    @Mock private ContentServiceClient contentServiceClient;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
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
        lenient().when(sendRateControlService.reserve(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyInt(), anyString()))
                .thenReturn(new SendRateControlService.RateLimitDecision(true, "key", 1, null, "ok", null, "evt-123", "NEW"));
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
                "contentReference", "cr_1234567890abcdef1234567890abcdef",
                "htmlContent", "Hello",
                "subject", "Hi"
        );
        payload = payloadWithPolicy(payload);
        
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
        when(providerStrategy.selectProvider("tenant-1", "workspace-1", "example.com")).thenReturn(result);
        when(contentProcessingService.processContent(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn("Processed HTML");

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(mockAdapter).sendEmail(eq("test@example.com"), anyString(), anyString(), anyMap(), eq(mockProvider));
        verify(sendRateControlService).settle("tenant-1", "workspace-1", "evt-123");
        verify(feedbackOutboxService).enqueueEmailSent(eq("tenant-1"), anyString(), eq("evt-123"), eq("camp-1"), any(), any(), eq("sub-1"), anyMap());
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
        payload = payloadWithPolicy(payload);

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
        when(providerStrategy.selectProvider("tenant-1", "workspace-1", "example.com")).thenReturn(result);
        when(contentProcessingService.processContent(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn("Processed HTML");

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        ArgumentCaptor<MessageLog> logCaptor = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogRepository).saveAndFlush(logCaptor.capture());
        assertEquals("cr_1234567890abcdef1234567890abcdef", logCaptor.getValue().getContentReference());
    }

    @Test
    void processSendRequest_MissingContentReferenceFailsTerminallyBeforeSafetyOrAdapter() {
        Map<String, Object> payload = Map.of(
                "email", "test@example.com",
                "subscriberId", "sub-1",
                "campaignId", "camp-1",
                "workspaceId", "workspace-1",
                "htmlContent", "Hello",
                "subject", "Hi"
        );

        stubNewLogClaim("tenant-1", "workspace-1", "evt-123");

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        ArgumentCaptor<MessageLog> logCaptor = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogRepository).save(logCaptor.capture());
        assertEquals(MessageLog.DeliveryStatus.FAILED.name(), logCaptor.getValue().getStatus());
        assertEquals("CONTENT_UNAVAILABLE", logCaptor.getValue().getFailureClass());
        assertEquals("Delivery contentReference is required", logCaptor.getValue().getProviderResponse());
        assertNull(logCaptor.getValue().getNextRetryAt());
        verify(inboxSafetyService, never()).evaluate(any());
        verify(providerStrategy, never()).selectProvider(anyString(), anyString(), anyString());
        verify(contentProcessingService, never()).processContent(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
        verify(feedbackOutboxService).enqueueEmailFailed(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("evt-123"),
                eq("camp-1"),
                isNull(),
                isNull(),
                eq("sub-1"),
                contains("contentReference"),
                anyMap());
        verify(feedbackOutboxService, never()).enqueueRetryScheduled(anyString(), anyString(), anyString(), anyLong(), anyString(), anyMap());
    }

    @Test
    void processSendRequest_UnresolvedReferenceFailsTerminallyBeforeSafetyOrAdapter() {
        Map<String, Object> payload = Map.of(
                "email", "test@example.com",
                "subscriberId", "sub-1",
                "campaignId", "camp-1",
                "workspaceId", "workspace-1",
                "messageId", "msg-1",
                "contentReference", "cr_missing"
        );

        stubNewLogClaim("tenant-1", "workspace-1", "msg-1");
        when(cacheService.get("email:content:cr_missing", String.class)).thenReturn(Optional.empty());
        when(contentServiceClient.fetchRenderedContent("tenant-1", "workspace-1", "cr_missing")).thenReturn(Map.of());

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        ArgumentCaptor<MessageLog> logCaptor = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogRepository).save(logCaptor.capture());
        assertEquals(MessageLog.DeliveryStatus.FAILED.name(), logCaptor.getValue().getStatus());
        assertEquals("CONTENT_UNAVAILABLE", logCaptor.getValue().getFailureClass());
        assertEquals("Delivery content is missing required subject/htmlBody for contentReference cr_missing",
                logCaptor.getValue().getProviderResponse());
        assertNull(logCaptor.getValue().getNextRetryAt());
        verify(contentServiceClient).fetchRenderedContent("tenant-1", "workspace-1", "cr_missing");
        verify(inboxSafetyService, never()).evaluate(any());
        verify(providerStrategy, never()).selectProvider(anyString(), anyString(), anyString());
        verify(contentProcessingService, never()).processContent(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
        verify(feedbackOutboxService).enqueueEmailFailed(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("msg-1"),
                eq("camp-1"),
                isNull(),
                isNull(),
                eq("sub-1"),
                contains("subject/htmlBody"),
                anyMap());
        verify(feedbackOutboxService, never()).enqueueRetryScheduled(anyString(), anyString(), anyString(), anyLong(), anyString(), anyMap());
    }

    @Test
    void processSendRequest_ResolvesPayloadContentReferenceWhenInlineBodyMissing() throws Exception {
        Map<String, Object> payload = Map.of(
                "email", "test@example.com",
                "subscriberId", "sub-1",
                "campaignId", "camp-1",
                "workspaceId", "workspace-1",
                "messageId", "msg-1",
                "contentReference", "cr_1234567890abcdef1234567890abcdef"
        );
        payload = payloadWithPolicy(payload);
        MockProviderAdapter mockAdapter = mock(MockProviderAdapter.class);
        SmtpProvider mockProvider = new SmtpProvider();
        mockProvider.setId("prov-1");
        ProviderSelectionStrategy.ProviderSelectionResult result = new ProviderSelectionStrategy.ProviderSelectionResult(mockAdapter, mockProvider);

        when(cacheService.get("email:content:cr_1234567890abcdef1234567890abcdef", String.class))
                .thenReturn(Optional.of("{\"tenantId\":\"tenant-1\",\"workspaceId\":\"workspace-1\",\"campaignId\":\"camp-1\",\"messageId\":\"msg-1\",\"subject\":\"Cached subject\",\"htmlBody\":\"<p>Cached body</p>\"}"));
        when(messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId("tenant-1", "workspace-1", "msg-1"))
                .thenReturn(Optional.empty());
        when(messageLogRepository.saveAndFlush(any(MessageLog.class))).thenAnswer(invocation -> {
            MessageLog log = invocation.getArgument(0, MessageLog.class);
            log.setId("log-1");
            return log;
        });
        when(messageLogRepository.claimForProcessing(nullable(String.class), eq(MessageLog.DeliveryStatus.PENDING.name()), eq(MessageLog.DeliveryStatus.PROCESSING.name()))).thenReturn(1);
        when(providerStrategy.selectProvider("tenant-1", "workspace-1", "example.com")).thenReturn(result);
        when(contentProcessingService.processContent(eq("<p>Cached body</p>"), eq("tenant-1"), eq("camp-1"),
                eq("sub-1"), eq("msg-1"), eq("workspace-1"), isNull(), isNull(), eq(false)))
                .thenReturn("Processed HTML");

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(mockAdapter).sendEmail(eq("test@example.com"), eq("Cached subject"), eq("Processed HTML"), anyMap(), eq(mockProvider));
        verify(contentServiceClient, never()).fetchRenderedContent(anyString(), anyString(), anyString());
    }

    @Test
    void processSendRequest_ResolvesPayloadContentReferenceFromContentServiceOnRedisMiss() throws Exception {
        Map<String, Object> payload = Map.of(
                "email", "test@example.com",
                "subscriberId", "sub-1",
                "campaignId", "camp-1",
                "workspaceId", "workspace-1",
                "messageId", "msg-1",
                "contentReference", "cr_1234567890abcdef1234567890abcdef"
        );
        payload = payloadWithPolicy(payload);
        Map<String, String> serviceContent = Map.of(
                "tenantId", "tenant-1",
                "workspaceId", "workspace-1",
                "campaignId", "camp-1",
                "messageId", "msg-1",
                "subject", "Service subject",
                "htmlBody", "<p>Service body</p>"
        );

        MockProviderAdapter mockAdapter = mock(MockProviderAdapter.class);
        SmtpProvider mockProvider = new SmtpProvider();
        mockProvider.setId("prov-1");
        ProviderSelectionStrategy.ProviderSelectionResult result = new ProviderSelectionStrategy.ProviderSelectionResult(mockAdapter, mockProvider);

        when(cacheService.get("email:content:cr_1234567890abcdef1234567890abcdef", String.class)).thenReturn(Optional.empty());
        when(contentServiceClient.fetchRenderedContent("tenant-1", "workspace-1", "cr_1234567890abcdef1234567890abcdef"))
                .thenReturn(serviceContent);
        when(messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId("tenant-1", "workspace-1", "msg-1"))
                .thenReturn(Optional.empty());
        when(messageLogRepository.saveAndFlush(any(MessageLog.class))).thenAnswer(invocation -> {
            MessageLog log = invocation.getArgument(0, MessageLog.class);
            log.setId("log-1");
            return log;
        });
        when(messageLogRepository.claimForProcessing(nullable(String.class), eq(MessageLog.DeliveryStatus.PENDING.name()), eq(MessageLog.DeliveryStatus.PROCESSING.name()))).thenReturn(1);
        when(providerStrategy.selectProvider("tenant-1", "workspace-1", "example.com")).thenReturn(result);
        when(contentProcessingService.processContent(eq("<p>Service body</p>"), eq("tenant-1"), eq("camp-1"),
                eq("sub-1"), eq("msg-1"), eq("workspace-1"), isNull(), isNull(), eq(false)))
                .thenReturn("Processed HTML");

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(mockAdapter).sendEmail(eq("test@example.com"), eq("Service subject"), eq("Processed HTML"), anyMap(), eq(mockProvider));
    }

    @Test
    void processSendRequest_MissingPolicySnapshotFailsBeforeSafetyOrAdapter() {
        Map<String, Object> payload = Map.of(
                "email", "test@example.com",
                "subscriberId", "sub-1",
                "campaignId", "camp-1",
                "workspaceId", "workspace-1",
                "messageId", "msg-1",
                "contentReference", "cr_1234567890abcdef1234567890abcdef",
                "htmlContent", "Hello",
                "subject", "Hi"
        );

        stubNewLogClaim("tenant-1", "workspace-1", "msg-1");

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        ArgumentCaptor<MessageLog> logCaptor = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogRepository).save(logCaptor.capture());
        assertEquals(MessageLog.DeliveryStatus.FAILED.name(), logCaptor.getValue().getStatus());
        assertEquals("SEND_GOVERNANCE_POLICY_BLOCKED", logCaptor.getValue().getFailureClass());
        assertEquals("Send governance policy snapshot is required before delivery execution",
                logCaptor.getValue().getProviderResponse());
        verify(inboxSafetyService, never()).evaluate(any());
        verify(providerStrategy, never()).selectProvider(anyString(), anyString(), anyString());
        verify(feedbackOutboxService).enqueueEmailFailed(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("msg-1"),
                eq("camp-1"),
                isNull(),
                isNull(),
                eq("sub-1"),
                contains("policy snapshot"),
                anyMap());
    }

    @Test
    void processSendRequest_StalePolicySnapshotHashFailsBeforeSafetyOrAdapter() {
        Map<String, Object> payload = payloadWithPolicy(Map.of(
                "email", "test@example.com",
                "subscriberId", "sub-1",
                "campaignId", "camp-1",
                "workspaceId", "workspace-1",
                "messageId", "msg-1",
                "contentReference", "cr_1234567890abcdef1234567890abcdef",
                "htmlContent", "Hello",
                "subject", "Hi"
        ));
        payload.put("sendGovernancePolicySnapshotHash", "0000000000000000000000000000000000000000000000000000000000000000");

        stubNewLogClaim("tenant-1", "workspace-1", "msg-1");

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        ArgumentCaptor<MessageLog> logCaptor = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogRepository).save(logCaptor.capture());
        assertEquals(MessageLog.DeliveryStatus.FAILED.name(), logCaptor.getValue().getStatus());
        assertEquals("SEND_GOVERNANCE_POLICY_BLOCKED", logCaptor.getValue().getFailureClass());
        assertEquals("Send governance policy snapshot hash does not match payload",
                logCaptor.getValue().getProviderResponse());
        verify(inboxSafetyService, never()).evaluate(any());
        verify(providerStrategy, never()).selectProvider(anyString(), anyString(), anyString());
    }

    @Test
    void processScheduledRetries_ResolvesCrReferenceFromRedisContent() throws Exception {
        MessageLog retry = retryLog("cr_1234567890abcdef1234567890abcdef");
        DeliveryOrchestrationService self = mock(DeliveryOrchestrationService.class);
        ReflectionTestUtils.setField(orchestrationService, "self", self);

        when(messageLogRepository.findEligibleForRetry(any(), any(Pageable.class))).thenReturn(List.of(retry));
        when(cacheService.get("email:content:cr_1234567890abcdef1234567890abcdef", String.class))
                .thenReturn(Optional.of("{\"tenantId\":\"tenant-1\",\"workspaceId\":\"workspace-1\",\"campaignId\":\"camp-1\",\"messageId\":\"msg-1\",\"subject\":\"Cached subject\",\"htmlBody\":\"<p>Cached body</p>\"}"));

        orchestrationService.processScheduledRetries();

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageLogRepository).findEligibleForRetry(any(), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(500, pageableCaptor.getValue().getPageSize());
        verify(self).processSendRequest(payloadCaptor.capture(), eq("tenant-1"), eq("msg-1"));
        assertEquals("Cached subject", payloadCaptor.getValue().get("subject"));
        assertEquals("<p>Cached body</p>", payloadCaptor.getValue().get("htmlBody"));
        assertEquals("cr_1234567890abcdef1234567890abcdef", payloadCaptor.getValue().get("contentReference"));
        verify(contentServiceClient, never()).fetchRenderedContent(anyString(), anyString(), anyString());
    }

    @Test
    void processScheduledRetries_ResolvesCrReferenceFromContentServiceOnRedisMiss() {
        MessageLog retry = retryLog("cr_1234567890abcdef1234567890abcdef");
        DeliveryOrchestrationService self = mock(DeliveryOrchestrationService.class);
        ReflectionTestUtils.setField(orchestrationService, "self", self);

        when(messageLogRepository.findEligibleForRetry(any(), any(Pageable.class))).thenReturn(List.of(retry));
        when(cacheService.get("email:content:cr_1234567890abcdef1234567890abcdef", String.class)).thenReturn(Optional.empty());
        when(contentServiceClient.fetchRenderedContent("tenant-1", "workspace-1", "cr_1234567890abcdef1234567890abcdef"))
                .thenReturn(Map.of(
                        "tenantId", "tenant-1",
                        "workspaceId", "workspace-1",
                        "campaignId", "camp-1",
                        "messageId", "msg-1",
                        "subject", "Service subject",
                        "htmlBody", "<p>Service body</p>"));

        orchestrationService.processScheduledRetries();

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(self).processSendRequest(payloadCaptor.capture(), eq("tenant-1"), eq("msg-1"));
        assertEquals("Service subject", payloadCaptor.getValue().get("subject"));
        assertEquals("<p>Service body</p>", payloadCaptor.getValue().get("htmlBody"));
        assertEquals("cr_1234567890abcdef1234567890abcdef", payloadCaptor.getValue().get("contentReference"));
    }

    @Test
    void processScheduledRetries_MissingCrReferenceDoesNotSendPlaceholderContent() {
        MessageLog retry = retryLog("cr_missing");
        DeliveryOrchestrationService self = mock(DeliveryOrchestrationService.class);
        ReflectionTestUtils.setField(orchestrationService, "self", self);

        when(messageLogRepository.findEligibleForRetry(any(), any(Pageable.class))).thenReturn(List.of(retry));
        when(cacheService.get("email:content:cr_missing", String.class)).thenReturn(Optional.empty());
        when(contentServiceClient.fetchRenderedContent("tenant-1", "workspace-1", "cr_missing")).thenReturn(Map.of());

        orchestrationService.processScheduledRetries();

        verify(self, never()).processSendRequest(anyMap(), anyString(), anyString());
        verify(contentServiceClient).fetchRenderedContent("tenant-1", "workspace-1", "cr_missing");
        ArgumentCaptor<MessageLog> logCaptor = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogRepository).save(logCaptor.capture());
        assertEquals(MessageLog.DeliveryStatus.FAILED.name(), logCaptor.getValue().getStatus());
        assertEquals("CONTENT_UNAVAILABLE", logCaptor.getValue().getFailureClass());
        verify(feedbackOutboxService).enqueueEmailFailed(
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
    void processScheduledRetries_NonCrContentMissDoesNotSendPlaceholderContent() {
        MessageLog retry = retryLog("ref:camp-1:msg-1");
        DeliveryOrchestrationService self = mock(DeliveryOrchestrationService.class);
        ReflectionTestUtils.setField(orchestrationService, "self", self);

        when(messageLogRepository.findEligibleForRetry(any(), any(Pageable.class))).thenReturn(List.of(retry));
        when(cacheService.get("email:content:ref:camp-1:msg-1", String.class)).thenReturn(Optional.empty());
        when(contentServiceClient.fetchRenderedContent("tenant-1", "workspace-1", "ref:camp-1:msg-1")).thenReturn(Map.of());

        orchestrationService.processScheduledRetries();

        verify(self, never()).processSendRequest(anyMap(), anyString(), anyString());
        ArgumentCaptor<MessageLog> logCaptor = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogRepository).save(logCaptor.capture());
        assertEquals(MessageLog.DeliveryStatus.FAILED.name(), logCaptor.getValue().getStatus());
        assertEquals("CONTENT_UNAVAILABLE", logCaptor.getValue().getFailureClass());
        verify(feedbackOutboxService).enqueueEmailFailed(
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

        when(messageLogRepository.findEligibleForRetry(any(), any(Pageable.class))).thenReturn(List.of(retry));
        when(cacheService.get("email:content:ref:camp-1:msg-1", String.class)).thenReturn(Optional.empty());
        when(contentServiceClient.fetchRenderedContent("tenant-1", "workspace-1", "ref:camp-1:msg-1"))
                .thenReturn(Map.of("subject", "Recovered subject"));

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
                "contentReference", "cr_1234567890abcdef1234567890abcdef",
                "htmlContent", "Hello",
                "subject", "Hi"
        );
        payload = payloadWithPolicy(payload);
        
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
        when(providerStrategy.selectProvider("tenant-1", "workspace-1", "example.com")).thenReturn(result);
        when(contentProcessingService.processContent(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn("Processed HTML");
        when(retryPolicyService.decide(any(), anyInt()))
                .thenReturn(new RetryPolicyService.RetryDecision(false, "HARD_BOUNCE", null, "permanent"));

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(feedbackOutboxService).enqueueEmailFailed(eq("tenant-1"), anyString(), eq("evt-123"), any(), any(), any(), any(), anyString(), anyMap());
        verify(feedbackOutboxService).enqueueEmailBounced(eq("tenant-1"), eq("workspace-1"), eq("bounce@example.com"), anyString(), eq("example.com"), anyMap());
        verify(sendRateControlService).release(eq("tenant-1"), eq("workspace-1"), eq("evt-123"), contains("PROVIDER_SEND_FAILED"));
    }

    @Test
    void processSendRequest_TransientFailure_SchedulesRetry() throws Exception {
        Map<String, Object> payload = Map.of(
                "email", "delay@example.com",
                "workspaceId", "workspace-1",
                "contentReference", "cr_1234567890abcdef1234567890abcdef",
                "htmlContent", "Hello",
                "subject", "Hi"
        );
        payload = payloadWithPolicy(payload);
        
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
        when(providerStrategy.selectProvider("tenant-1", "workspace-1", "example.com")).thenReturn(result);
        when(contentProcessingService.processContent(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn("Processed HTML");
        when(retryPolicyService.decide(any(), anyInt()))
                .thenReturn(new RetryPolicyService.RetryDecision(true, "NETWORK", Instant.now().plusSeconds(60), "retry"));

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(feedbackOutboxService).enqueueRetryScheduled(eq("tenant-1"), eq("workspace-1"), eq("evt-123"), eq(1L), anyString(), anyMap());
        verify(sendRateControlService).release(eq("tenant-1"), eq("workspace-1"), eq("evt-123"), contains("PROVIDER_SEND_FAILED"));
    }

    @Test
    void processSendRequest_InvalidEmail_DoesNotSelectProviderAndMarksFailed() {
        Map<String, Object> payload = Map.of(
                "email", "invalid-email",
                "workspaceId", "workspace-1",
                "contentReference", "cr_1234567890abcdef1234567890abcdef",
                "htmlContent", "Hello",
                "subject", "Hi"
        );
        payload = payloadWithPolicy(payload);

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

        verify(providerStrategy, never()).selectProvider(anyString(), anyString(), anyString());
        verify(feedbackOutboxService).enqueueEmailFailed(eq("tenant-1"), anyString(), eq("evt-123"), any(), any(), any(), any(), anyString(), anyMap());
        verify(feedbackOutboxService).enqueueEmailBounced(eq("tenant-1"), eq("workspace-1"), eq("invalid-email"), anyString(), isNull(), anyMap());
    }

    @Test
    void processSendRequest_AlreadyProcessedMessage_SkipsDispatch() {
        MessageLog messageLog = new MessageLog();
        messageLog.setStatus(MessageLog.DeliveryStatus.SENT.name());

        when(messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId("tenant-1", "workspace-1", "evt-123")).thenReturn(Optional.of(messageLog));

        orchestrationService.processSendRequest(Map.of("email", "test@example.com", "workspaceId", "workspace-1"), "tenant-1", "evt-123");

        verify(providerStrategy, never()).selectProvider(anyString(), anyString(), anyString());
        verify(feedbackOutboxService, never()).enqueueEmailSent(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyMap());
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
        log.setSendGovernancePolicyId("policy-1");
        log.setSendGovernancePolicyKey("promo.default");
        log.setSendGovernancePolicyVersion(1L);
        log.setSendGovernancePolicySnapshotHash(validPolicySnapshotHash());
        log.setSendGovernancePolicySnapshot(validPolicySnapshotJson());
        return log;
    }

    private Map<String, Object> payloadWithPolicy(Map<String, Object> payload) {
        Map<String, Object> enriched = new java.util.HashMap<>(payload);
        enriched.put("sendGovernancePolicyId", "policy-1");
        enriched.put("sendGovernancePolicyKey", "promo.default");
        enriched.put("sendGovernancePolicyVersion", 1L);
        enriched.put("sendGovernancePolicySnapshotHash", validPolicySnapshotHash());
        enriched.put("sendGovernancePolicySnapshot", validPolicySnapshotJson());
        return enriched;
    }

    private String validPolicySnapshotJson() {
        return "{\"active\":true,\"classification\":\"COMMERCIAL\",\"commercial\":true,\"consentRequired\":false,"
                + "\"deliveryProfileId\":\"delivery-1\",\"policyId\":\"policy-1\",\"policyKey\":\"promo.default\","
                + "\"providerId\":\"provider-1\",\"publicationPolicy\":\"APPROVED_CONTENT_REQUIRED\","
                + "\"sendLogRetentionDays\":365,\"senderProfileId\":\"sender-1\",\"sendingDomain\":\"example.com\","
                + "\"suppressionRequired\":true,\"trackingAllowed\":true,\"unsubscribePolicy\":\"REQUIRED\",\"version\":1}";
    }

    private String validPolicySnapshotHash() {
        return "6717da725a08e8a4d65050d8a76a7228d2661c40ad55e035520273cd972fc2f3";
    }

    private void stubNewLogClaim(String tenantId, String workspaceId, String messageId) {
        when(messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId(tenantId, workspaceId, messageId))
                .thenReturn(Optional.empty());
        when(messageLogRepository.saveAndFlush(any(MessageLog.class))).thenAnswer(invocation -> {
            MessageLog log = invocation.getArgument(0, MessageLog.class);
            log.setId("log-1");
            return log;
        });
        when(messageLogRepository.claimForProcessing(nullable(String.class),
                eq(MessageLog.DeliveryStatus.PENDING.name()),
                eq(MessageLog.DeliveryStatus.PROCESSING.name()))).thenReturn(1);
    }
}

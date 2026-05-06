package com.legent.delivery.service;

import java.util.Optional;
import java.util.Map;

import com.legent.delivery.adapter.ProviderDispatchException;
import com.legent.delivery.adapter.impl.MockProviderAdapter;
import com.legent.delivery.domain.MessageLog;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.event.DeliveryEventPublisher;
import com.legent.delivery.repository.MessageLogRepository;
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

    @InjectMocks private DeliveryOrchestrationService orchestrationService;

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
        verify(eventPublisher).publishEmailSent(eq("tenant-1"), anyString(), eq("evt-123"), eq("camp-1"), any(), any(), eq("sub-1"));
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

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(eventPublisher).publishEmailFailed(eq("tenant-1"), anyString(), eq("evt-123"), any(), any(), any(), any(), anyString());
        verify(eventPublisher).publishEmailBounced(eq("tenant-1"), eq("workspace-1"), eq("bounce@example.com"), anyString(), eq("example.com"));
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

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(eventPublisher).publishRetryScheduled(eq("tenant-1"), eq("workspace-1"), eq("evt-123"), eq(1L), anyString());
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

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(providerStrategy, never()).selectProvider(anyString(), anyString());
        verify(eventPublisher).publishEmailFailed(eq("tenant-1"), anyString(), eq("evt-123"), any(), any(), any(), any(), anyString());
        verify(eventPublisher).publishEmailBounced(eq("tenant-1"), eq("workspace-1"), eq("invalid-email"), anyString(), isNull());
    }

    @Test
    void processSendRequest_AlreadyProcessedMessage_SkipsDispatch() {
        MessageLog messageLog = new MessageLog();
        messageLog.setStatus(MessageLog.DeliveryStatus.SENT.name());

        when(messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId("tenant-1", "workspace-1", "evt-123")).thenReturn(Optional.of(messageLog));

        orchestrationService.processSendRequest(Map.of("email", "test@example.com", "workspaceId", "workspace-1"), "tenant-1", "evt-123");

        verify(providerStrategy, never()).selectProvider(anyString(), anyString());
        verify(eventPublisher, never()).publishEmailSent(anyString(), anyString(), anyString(), any(), any(), any(), any());
        verify(messageLogRepository, never()).save(any(MessageLog.class));
    }
}

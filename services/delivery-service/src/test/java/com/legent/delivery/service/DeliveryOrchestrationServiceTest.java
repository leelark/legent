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
                "htmlContent", "Hello",
                "subject", "Hi"
        );
        
        MockProviderAdapter mockAdapter = mock(MockProviderAdapter.class);
        SmtpProvider mockProvider = new SmtpProvider();
        mockProvider.setId("prov-1");
        ProviderSelectionStrategy.ProviderSelectionResult result = new ProviderSelectionStrategy.ProviderSelectionResult(mockAdapter, mockProvider);
        
        when(messageLogRepository.findByTenantIdAndMessageId(any(), any())).thenReturn(Optional.empty());
        when(messageLogRepository.save(any(MessageLog.class))).thenAnswer(invocation -> invocation.getArgument(0, MessageLog.class));
        when(providerStrategy.selectProvider("tenant-1", "example.com")).thenReturn(result);
        when(contentProcessingService.processContent(any(), any(), any(), any(), any())).thenReturn("Processed HTML");

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(mockAdapter).sendEmail(eq("test@example.com"), anyString(), anyString(), anyMap(), eq(mockProvider));
        verify(eventPublisher).publishEmailSent("tenant-1", "evt-123", "camp-1", "sub-1");
    }

    @Test
    void processSendRequest_PermanentFailure() throws Exception {
        Map<String, Object> payload = Map.of(
                "email", "bounce@example.com",
                "htmlContent", "Hello",
                "subject", "Hi"
        );
        
        SmtpProvider mockProvider = new SmtpProvider();
        MockProviderAdapter mockAdapter = mock(MockProviderAdapter.class);
        doThrow(new ProviderDispatchException("Hard bounce", true)).when(mockAdapter).sendEmail(any(), any(), any(), any(), eq(mockProvider));
        
        ProviderSelectionStrategy.ProviderSelectionResult result = new ProviderSelectionStrategy.ProviderSelectionResult(mockAdapter, mockProvider);
        
        when(messageLogRepository.findByTenantIdAndMessageId(any(), any())).thenReturn(Optional.empty());
        when(messageLogRepository.save(any(MessageLog.class))).thenAnswer(invocation -> invocation.getArgument(0, MessageLog.class));
        when(providerStrategy.selectProvider("tenant-1", "example.com")).thenReturn(result);
        when(contentProcessingService.processContent(any(), any(), any(), any(), any())).thenReturn("Processed HTML");

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(eventPublisher).publishEmailFailed(eq("tenant-1"), eq("evt-123"), any(), any(), anyString());
        verify(eventPublisher).publishEmailBounced(eq("tenant-1"), eq("bounce@example.com"), anyString(), eq("example.com"));
    }

    @Test
    void processSendRequest_TransientFailure_SchedulesRetry() throws Exception {
        Map<String, Object> payload = Map.of(
                "email", "delay@example.com",
                "htmlContent", "Hello",
                "subject", "Hi"
        );
        
        SmtpProvider mockProvider = new SmtpProvider();
        MockProviderAdapter mockAdapter = mock(MockProviderAdapter.class);
        doThrow(new ProviderDispatchException("Timeout", false)).when(mockAdapter).sendEmail(any(), any(), any(), any(), eq(mockProvider));
        
        ProviderSelectionStrategy.ProviderSelectionResult result = new ProviderSelectionStrategy.ProviderSelectionResult(mockAdapter, mockProvider);
        
        when(messageLogRepository.findByTenantIdAndMessageId(any(), any())).thenReturn(Optional.empty());
        when(messageLogRepository.save(any(MessageLog.class))).thenAnswer(invocation -> invocation.getArgument(0, MessageLog.class));
        when(providerStrategy.selectProvider("tenant-1", "example.com")).thenReturn(result);
        when(contentProcessingService.processContent(any(), any(), any(), any(), any())).thenReturn("Processed HTML");

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(eventPublisher).publishRetryScheduled(eq("tenant-1"), eq("evt-123"), eq(1L), anyString());
    }

    @Test
    void processSendRequest_InvalidEmail_DoesNotSelectProviderAndMarksFailed() {
        Map<String, Object> payload = Map.of(
                "email", "invalid-email"
        );

        when(messageLogRepository.findByTenantIdAndMessageId("tenant-1", "evt-123")).thenReturn(Optional.empty());
        when(messageLogRepository.save(any(MessageLog.class))).thenAnswer(invocation -> invocation.getArgument(0, MessageLog.class));

        orchestrationService.processSendRequest(payload, "tenant-1", "evt-123");

        verify(providerStrategy, never()).selectProvider(anyString(), anyString());
        verify(eventPublisher).publishEmailFailed(eq("tenant-1"), eq("evt-123"), any(), any(), anyString());
        verify(eventPublisher).publishEmailBounced(eq("tenant-1"), eq("invalid-email"), anyString(), eq(""));
    }

    @Test
    void processSendRequest_AlreadyProcessedMessage_SkipsDispatch() {
        MessageLog messageLog = new MessageLog();
        messageLog.setStatus(MessageLog.DeliveryStatus.SENT.name());

        when(messageLogRepository.findByTenantIdAndMessageId("tenant-1", "evt-123")).thenReturn(Optional.of(messageLog));

        orchestrationService.processSendRequest(Map.of("email", "test@example.com"), "tenant-1", "evt-123");

        verify(providerStrategy, never()).selectProvider(anyString(), anyString());
        verify(eventPublisher, never()).publishEmailSent(anyString(), anyString(), any(), any());
        verify(messageLogRepository, never()).save(any(MessageLog.class));
    }
}

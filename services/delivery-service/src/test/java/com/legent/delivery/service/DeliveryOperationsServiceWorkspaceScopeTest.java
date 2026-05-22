package com.legent.delivery.service;

import com.legent.delivery.domain.DeliveryReplayQueue;
import com.legent.delivery.domain.MessageLog;
import com.legent.delivery.domain.ProviderHealthStatus;
import com.legent.delivery.repository.DeliveryReplayQueueRepository;
import com.legent.delivery.repository.MessageLogRepository;
import com.legent.delivery.repository.ProviderHealthStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryOperationsServiceWorkspaceScopeTest {

    @Mock private MessageLogRepository messageLogRepository;
    @Mock private DeliveryReplayQueueRepository replayQueueRepository;
    @Mock private ProviderHealthStatusRepository providerHealthStatusRepository;
    @Mock private DeliveryOrchestrationService orchestrationService;

    private DeliveryOperationsService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryOperationsService(
                messageLogRepository,
                replayQueueRepository,
                providerHealthStatusRepository,
                orchestrationService);
    }

    @Test
    void queueStats_usesTenantAndWorkspaceForProviderHealth() {
        ProviderHealthStatus unhealthy = new ProviderHealthStatus();
        unhealthy.setCurrentStatus(ProviderHealthStatus.HealthStatus.UNHEALTHY);
        when(providerHealthStatusRepository.findByTenantIdAndWorkspaceId("tenant-1", "workspace-a"))
                .thenReturn(List.of(unhealthy));

        Map<String, Object> stats = service.queueStats("tenant-1", "workspace-a");

        assertEquals(1L, stats.get("unhealthyProviders"));
        verify(providerHealthStatusRepository).findByTenantIdAndWorkspaceId("tenant-1", "workspace-a");
    }

    @Test
    void retryMessage_deniesSiblingWorkspaceMessage() {
        when(messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId("tenant-1", "workspace-a", "message-1"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.retryMessage(
                "tenant-1",
                "workspace-a",
                "message-1",
                "manual"));

        verify(messageLogRepository, never()).save(any());
    }

    @Test
    void enqueueReplay_deniesSiblingWorkspaceMessage() {
        when(messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId("tenant-1", "workspace-a", "message-1"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.enqueueReplay(
                "tenant-1",
                "workspace-a",
                "message-1",
                "manual"));

        verify(replayQueueRepository, never()).save(any(DeliveryReplayQueue.class));
    }

    @Test
    void recentMessages_usesTenantAndWorkspaceScope() {
        service.recentMessages("tenant-1", "workspace-a", 50);

        verify(messageLogRepository).findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                "tenant-1",
                "workspace-a",
                PageRequest.of(0, 50));
    }

    @Test
    void processReplayQueueUsesBoundedPageAndCompletesClaimedReplay() {
        DeliveryReplayQueue replay = replay("replay-1", "message-1");
        MessageLog source = sourceMessage("message-1");
        when(replayQueueRepository.findByTenantIdAndWorkspaceIdAndStatusAndScheduledAtLessThanEqualOrderByPriorityAscScheduledAtAsc(
                eq("tenant-1"),
                eq("workspace-a"),
                eq(DeliveryReplayQueue.ReplayStatus.PENDING.name()),
                any(Instant.class),
                eq(PageRequest.of(0, 2))))
                .thenReturn(List.of(replay));
        when(replayQueueRepository.claimForProcessing(
                "tenant-1",
                "workspace-a",
                "replay-1",
                DeliveryReplayQueue.ReplayStatus.PENDING.name(),
                DeliveryReplayQueue.ReplayStatus.PROCESSING.name()))
                .thenReturn(1);
        when(messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId("tenant-1", "workspace-a", "message-1"))
                .thenReturn(Optional.of(source));
        when(replayQueueRepository.markCompleted(
                eq("tenant-1"),
                eq("workspace-a"),
                eq("replay-1"),
                eq(DeliveryReplayQueue.ReplayStatus.PROCESSING.name()),
                eq(DeliveryReplayQueue.ReplayStatus.COMPLETED.name()),
                any(Instant.class)))
                .thenReturn(1);

        int processed = service.processReplayQueue("tenant-1", "workspace-a", 2);

        assertEquals(1, processed);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(orchestrationService).processSendRequest(payloadCaptor.capture(), eq("tenant-1"), anyString());
        assertEquals("cr_1234567890abcdef1234567890abcdef", payloadCaptor.getValue().get("contentReference"));
        verify(replayQueueRepository).markCompleted(
                eq("tenant-1"),
                eq("workspace-a"),
                eq("replay-1"),
                eq(DeliveryReplayQueue.ReplayStatus.PROCESSING.name()),
                eq(DeliveryReplayQueue.ReplayStatus.COMPLETED.name()),
                any(Instant.class));
        verify(replayQueueRepository, never()).save(any(DeliveryReplayQueue.class));
    }

    @Test
    void processReplayQueueSkipsReplayClaimedByAnotherWorker() {
        DeliveryReplayQueue replay = replay("replay-1", "message-1");
        when(replayQueueRepository.findByTenantIdAndWorkspaceIdAndStatusAndScheduledAtLessThanEqualOrderByPriorityAscScheduledAtAsc(
                eq("tenant-1"),
                eq("workspace-a"),
                eq(DeliveryReplayQueue.ReplayStatus.PENDING.name()),
                any(Instant.class),
                eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(replay));
        when(replayQueueRepository.claimForProcessing(
                "tenant-1",
                "workspace-a",
                "replay-1",
                DeliveryReplayQueue.ReplayStatus.PENDING.name(),
                DeliveryReplayQueue.ReplayStatus.PROCESSING.name()))
                .thenReturn(0);

        int processed = service.processReplayQueue("tenant-1", "workspace-a", 10);

        assertEquals(0, processed);
        verify(messageLogRepository, never()).findByTenantIdAndWorkspaceIdAndMessageId(anyString(), anyString(), anyString());
        verify(orchestrationService, never()).processSendRequest(any(), anyString(), anyString());
        verify(replayQueueRepository, never()).markCompleted(anyString(), anyString(), anyString(), anyString(), anyString(), any());
        verify(replayQueueRepository, never()).markFailed(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void processReplayQueueMarksFailedWhenClaimedReplayCannotLoadSourceMessage() {
        DeliveryReplayQueue replay = replay("replay-1", "missing-message");
        when(replayQueueRepository.findByTenantIdAndWorkspaceIdAndStatusAndScheduledAtLessThanEqualOrderByPriorityAscScheduledAtAsc(
                eq("tenant-1"),
                eq("workspace-a"),
                eq(DeliveryReplayQueue.ReplayStatus.PENDING.name()),
                any(Instant.class),
                eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(replay));
        when(replayQueueRepository.claimForProcessing(
                "tenant-1",
                "workspace-a",
                "replay-1",
                DeliveryReplayQueue.ReplayStatus.PENDING.name(),
                DeliveryReplayQueue.ReplayStatus.PROCESSING.name()))
                .thenReturn(1);
        when(messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId("tenant-1", "workspace-a", "missing-message"))
                .thenReturn(Optional.empty());

        int processed = service.processReplayQueue("tenant-1", "workspace-a", 1);

        assertEquals(1, processed);
        verify(orchestrationService, never()).processSendRequest(any(), anyString(), anyString());
        verify(replayQueueRepository).markFailed(
                "tenant-1",
                "workspace-a",
                "replay-1",
                DeliveryReplayQueue.ReplayStatus.PROCESSING.name(),
                DeliveryReplayQueue.ReplayStatus.FAILED.name(),
                "Missing source message for replay");
    }

    @Test
    void processReplayQueueMarksFailedWhenSourceMessageHasNoContentReference() {
        DeliveryReplayQueue replay = replay("replay-1", "message-1");
        MessageLog source = sourceMessage("message-1");
        source.setContentReference(null);
        when(replayQueueRepository.findByTenantIdAndWorkspaceIdAndStatusAndScheduledAtLessThanEqualOrderByPriorityAscScheduledAtAsc(
                eq("tenant-1"),
                eq("workspace-a"),
                eq(DeliveryReplayQueue.ReplayStatus.PENDING.name()),
                any(Instant.class),
                eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(replay));
        when(replayQueueRepository.claimForProcessing(
                "tenant-1",
                "workspace-a",
                "replay-1",
                DeliveryReplayQueue.ReplayStatus.PENDING.name(),
                DeliveryReplayQueue.ReplayStatus.PROCESSING.name()))
                .thenReturn(1);
        when(messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId("tenant-1", "workspace-a", "message-1"))
                .thenReturn(Optional.of(source));

        int processed = service.processReplayQueue("tenant-1", "workspace-a", 1);

        assertEquals(1, processed);
        verify(orchestrationService, never()).processSendRequest(any(), anyString(), anyString());
        verify(replayQueueRepository).markFailed(
                "tenant-1",
                "workspace-a",
                "replay-1",
                DeliveryReplayQueue.ReplayStatus.PROCESSING.name(),
                DeliveryReplayQueue.ReplayStatus.FAILED.name(),
                "Source message contentReference is required for replay");
    }

    private DeliveryReplayQueue replay(String id, String messageId) {
        DeliveryReplayQueue replay = new DeliveryReplayQueue();
        replay.setId(id);
        replay.setTenantId("tenant-1");
        replay.setWorkspaceId("workspace-a");
        replay.setOriginalMessageId(messageId);
        replay.setEmail("user@example.com");
        replay.setCampaignId("campaign-1");
        replay.setSubscriberId("subscriber-1");
        replay.setSourceJobId("job-1");
        replay.setSourceBatchId("batch-1");
        replay.setStatus(DeliveryReplayQueue.ReplayStatus.PENDING.name());
        replay.setScheduledAt(Instant.now());
        return replay;
    }

    private MessageLog sourceMessage(String messageId) {
        MessageLog source = new MessageLog();
        source.setTenantId("tenant-1");
        source.setWorkspaceId("workspace-a");
        source.setMessageId(messageId);
        source.setEmail("user@example.com");
        source.setFromEmail("sender@example.com");
        source.setFromName("Sender");
        source.setReplyToEmail("reply@example.com");
        source.setContentReference("cr_1234567890abcdef1234567890abcdef");
        return source;
    }
}

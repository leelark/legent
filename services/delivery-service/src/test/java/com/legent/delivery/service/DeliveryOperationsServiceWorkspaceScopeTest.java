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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
}

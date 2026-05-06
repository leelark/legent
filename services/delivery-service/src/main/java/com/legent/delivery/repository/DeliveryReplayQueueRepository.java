package com.legent.delivery.repository;

import com.legent.delivery.domain.DeliveryReplayQueue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface DeliveryReplayQueueRepository extends JpaRepository<DeliveryReplayQueue, String> {

    List<DeliveryReplayQueue> findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(String tenantId, String workspaceId);

    List<DeliveryReplayQueue> findByTenantIdAndWorkspaceIdAndStatusOrderByPriorityAscScheduledAtAsc(String tenantId,
                                                                                                      String workspaceId,
                                                                                                      String status);

    long countByTenantIdAndWorkspaceIdAndStatus(String tenantId, String workspaceId, String status);

    List<DeliveryReplayQueue> findByTenantIdAndWorkspaceIdAndStatusAndScheduledAtLessThanEqualOrderByPriorityAscScheduledAtAsc(
            String tenantId,
            String workspaceId,
            String status,
            Instant scheduledAt
    );
}


package com.legent.delivery.repository;

import com.legent.delivery.domain.DeliveryReplayQueue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    List<DeliveryReplayQueue> findByTenantIdAndWorkspaceIdAndStatusAndScheduledAtLessThanEqualOrderByPriorityAscScheduledAtAsc(
            String tenantId,
            String workspaceId,
            String status,
            Instant scheduledAt,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE DeliveryReplayQueue replay
               SET replay.status = :processingStatus,
                   replay.errorMessage = null
             WHERE replay.tenantId = :tenantId
               AND replay.workspaceId = :workspaceId
               AND replay.id = :id
               AND replay.status = :pendingStatus
            """)
    int claimForProcessing(@Param("tenantId") String tenantId,
                           @Param("workspaceId") String workspaceId,
                           @Param("id") String id,
                           @Param("pendingStatus") String pendingStatus,
                           @Param("processingStatus") String processingStatus);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE DeliveryReplayQueue replay
               SET replay.status = :completedStatus,
                   replay.processedAt = :processedAt,
                   replay.errorMessage = null
             WHERE replay.tenantId = :tenantId
               AND replay.workspaceId = :workspaceId
               AND replay.id = :id
               AND replay.status = :processingStatus
            """)
    int markCompleted(@Param("tenantId") String tenantId,
                      @Param("workspaceId") String workspaceId,
                      @Param("id") String id,
                      @Param("processingStatus") String processingStatus,
                      @Param("completedStatus") String completedStatus,
                      @Param("processedAt") Instant processedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE DeliveryReplayQueue replay
               SET replay.status = :failedStatus,
                   replay.retryCount = COALESCE(replay.retryCount, 0) + 1,
                   replay.errorMessage = :errorMessage
             WHERE replay.tenantId = :tenantId
               AND replay.workspaceId = :workspaceId
               AND replay.id = :id
               AND replay.status = :processingStatus
            """)
    int markFailed(@Param("tenantId") String tenantId,
                   @Param("workspaceId") String workspaceId,
                   @Param("id") String id,
                   @Param("processingStatus") String processingStatus,
                   @Param("failedStatus") String failedStatus,
                   @Param("errorMessage") String errorMessage);
}

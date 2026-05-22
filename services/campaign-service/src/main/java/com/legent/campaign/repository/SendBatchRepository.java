package com.legent.campaign.repository;

import java.util.List;
import java.time.Instant;

import com.legent.campaign.domain.SendBatch;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface SendBatchRepository extends JpaRepository<SendBatch, String> {
    List<SendBatch> findByTenantIdAndJobIdAndDeletedAtIsNull(String tenantId, String jobId);

    List<SendBatch> findByTenantIdAndWorkspaceIdAndJobIdAndDeletedAtIsNull(String tenantId, String workspaceId, String jobId);

    List<SendBatch> findByTenantIdAndStatusInAndDeletedAtIsNull(String tenantId, List<SendBatch.BatchStatus> statuses);

    @Modifying
    @Query("UPDATE SendBatch b SET b.status = :status WHERE b.jobId = :jobId AND b.tenantId = :tenantId")
    void updateStatusByJobId(@Param("tenantId") String tenantId, @Param("jobId") String jobId, @Param("status") SendBatch.BatchStatus status);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE SendBatch b
            SET b.status = :processingStatus,
                b.updatedAt = :claimedAt
            WHERE b.tenantId = :tenantId
              AND b.workspaceId = :workspaceId
              AND b.jobId = :jobId
              AND b.id = :batchId
              AND b.deletedAt IS NULL
              AND (
                    b.status = :pendingStatus
                    OR (
                        b.status = :failedStatus
                        AND (b.retryCount IS NULL OR b.retryCount < :maxRetries)
                    )
              )
            """)
    int claimRetryableBatchForProcessing(@Param("tenantId") String tenantId,
                                         @Param("workspaceId") String workspaceId,
                                         @Param("jobId") String jobId,
                                         @Param("batchId") String batchId,
                                         @Param("processingStatus") SendBatch.BatchStatus processingStatus,
                                         @Param("pendingStatus") SendBatch.BatchStatus pendingStatus,
                                         @Param("failedStatus") SendBatch.BatchStatus failedStatus,
                                         @Param("maxRetries") int maxRetries,
                                         @Param("claimedAt") Instant claimedAt);

    List<SendBatch> findByStatus(SendBatch.BatchStatus status);

    List<SendBatch> findByStatusAndDeletedAtIsNullOrderByUpdatedAtAscCreatedAtAsc(SendBatch.BatchStatus status, Pageable pageable);

    List<SendBatch> findByStatusAndUpdatedAtBeforeAndDeletedAtIsNull(SendBatch.BatchStatus status, Instant updatedBefore);

    List<SendBatch> findByStatusAndUpdatedAtBeforeAndDeletedAtIsNullOrderByUpdatedAtAscCreatedAtAsc(
            SendBatch.BatchStatus status,
            Instant updatedBefore,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE SendBatch b
            SET b.status = :partialStatus,
                b.lastError = :lastError,
                b.updatedAt = :claimedAt
            WHERE b.tenantId = :tenantId
              AND b.workspaceId = :workspaceId
              AND b.id = :batchId
              AND b.status = :processingStatus
              AND b.updatedAt < :updatedBefore
              AND b.deletedAt IS NULL
            """)
    int claimStaleProcessingBatchAsPartial(@Param("tenantId") String tenantId,
                                           @Param("workspaceId") String workspaceId,
                                           @Param("batchId") String batchId,
                                           @Param("processingStatus") SendBatch.BatchStatus processingStatus,
                                           @Param("partialStatus") SendBatch.BatchStatus partialStatus,
                                           @Param("updatedBefore") Instant updatedBefore,
                                           @Param("lastError") String lastError,
                                           @Param("claimedAt") Instant claimedAt);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE SendBatch b
            SET b.status = :pendingStatus,
                b.retryCount = :nextRetryCount,
                b.updatedAt = :claimedAt
            WHERE b.tenantId = :tenantId
              AND b.workspaceId = :workspaceId
              AND b.id = :batchId
              AND b.status = :partialStatus
              AND COALESCE(b.retryCount, 0) = :currentRetryCount
              AND b.deletedAt IS NULL
            """)
    int claimPartialBatchForRetry(@Param("tenantId") String tenantId,
                                  @Param("workspaceId") String workspaceId,
                                  @Param("batchId") String batchId,
                                  @Param("partialStatus") SendBatch.BatchStatus partialStatus,
                                  @Param("pendingStatus") SendBatch.BatchStatus pendingStatus,
                                  @Param("currentRetryCount") int currentRetryCount,
                                  @Param("nextRetryCount") int nextRetryCount,
                                  @Param("claimedAt") Instant claimedAt);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE SendBatch b
            SET b.status = :failedStatus,
                b.retryCount = :nextRetryCount,
                b.lastError = :lastError,
                b.updatedAt = :claimedAt
            WHERE b.tenantId = :tenantId
              AND b.workspaceId = :workspaceId
              AND b.id = :batchId
              AND b.status = :partialStatus
              AND COALESCE(b.retryCount, 0) = :currentRetryCount
              AND b.deletedAt IS NULL
            """)
    int claimPartialBatchAsFailed(@Param("tenantId") String tenantId,
                                  @Param("workspaceId") String workspaceId,
                                  @Param("batchId") String batchId,
                                  @Param("partialStatus") SendBatch.BatchStatus partialStatus,
                                  @Param("failedStatus") SendBatch.BatchStatus failedStatus,
                                  @Param("currentRetryCount") int currentRetryCount,
                                  @Param("nextRetryCount") int nextRetryCount,
                                  @Param("lastError") String lastError,
                                  @Param("claimedAt") Instant claimedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE SendBatch b
            SET b.processedCount = COALESCE(b.processedCount, 0) + 1,
                b.successCount = COALESCE(b.successCount, 0) + CASE WHEN :failed = false THEN 1 ELSE 0 END,
                b.failureCount = COALESCE(b.failureCount, 0) + CASE WHEN :failed = true THEN 1 ELSE 0 END,
                b.lastError = CASE WHEN :failed = true AND :lastError IS NOT NULL THEN :lastError ELSE b.lastError END,
                b.status = CASE
                    WHEN COALESCE(b.processedCount, 0) + 1 >= COALESCE(b.batchSize, 0) THEN
                        CASE
                            WHEN COALESCE(b.failureCount, 0) + CASE WHEN :failed = true THEN 1 ELSE 0 END > 0 THEN
                                CASE
                                    WHEN COALESCE(b.successCount, 0) + CASE WHEN :failed = false THEN 1 ELSE 0 END > 0
                                        THEN :partialStatus
                                    ELSE :failedStatus
                                END
                            ELSE :completedStatus
                        END
                    WHEN b.status = :pendingStatus THEN :processingStatus
                    ELSE b.status
                END,
                b.updatedAt = :updatedAt
            WHERE b.tenantId = :tenantId
              AND b.workspaceId = :workspaceId
              AND b.id = :batchId
              AND b.deletedAt IS NULL
            """)
    int applyDeliveryFeedbackCounters(@Param("tenantId") String tenantId,
                                      @Param("workspaceId") String workspaceId,
                                      @Param("batchId") String batchId,
                                      @Param("failed") boolean failed,
                                      @Param("lastError") String lastError,
                                      @Param("pendingStatus") SendBatch.BatchStatus pendingStatus,
                                      @Param("processingStatus") SendBatch.BatchStatus processingStatus,
                                      @Param("completedStatus") SendBatch.BatchStatus completedStatus,
                                      @Param("failedStatus") SendBatch.BatchStatus failedStatus,
                                      @Param("partialStatus") SendBatch.BatchStatus partialStatus,
                                      @Param("updatedAt") Instant updatedAt);

    List<SendBatch> findByJobIdAndStatus(String jobId, SendBatch.BatchStatus status);

    @Query("SELECT b FROM SendBatch b WHERE b.tenantId = :tenantId AND b.workspaceId = :workspaceId AND b.id = :batchId AND b.deletedAt IS NULL")
    java.util.Optional<SendBatch> findByTenantWorkspaceAndId(@Param("tenantId") String tenantId,
                                                              @Param("workspaceId") String workspaceId,
                                                              @Param("batchId") String batchId);

    @Query("SELECT COUNT(b) FROM SendBatch b WHERE b.tenantId = :tenantId AND b.workspaceId = :workspaceId AND b.jobId = :jobId AND b.deletedAt IS NULL")
    long countByTenantWorkspaceAndJob(@Param("tenantId") String tenantId,
                                      @Param("workspaceId") String workspaceId,
                                      @Param("jobId") String jobId);

    @Query("SELECT COUNT(b) FROM SendBatch b WHERE b.tenantId = :tenantId AND b.workspaceId = :workspaceId AND b.jobId = :jobId AND b.status IN :statuses AND b.deletedAt IS NULL")
    long countByTenantWorkspaceAndJobAndStatuses(@Param("tenantId") String tenantId,
                                                 @Param("workspaceId") String workspaceId,
                                                 @Param("jobId") String jobId,
                                                 @Param("statuses") List<SendBatch.BatchStatus> statuses);
}

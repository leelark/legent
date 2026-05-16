package com.legent.campaign.repository;

import java.util.List;
import java.time.Instant;

import com.legent.campaign.domain.SendBatch;
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

    List<SendBatch> findByStatus(SendBatch.BatchStatus status);

    List<SendBatch> findByStatusAndUpdatedAtBeforeAndDeletedAtIsNull(SendBatch.BatchStatus status, Instant updatedBefore);

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

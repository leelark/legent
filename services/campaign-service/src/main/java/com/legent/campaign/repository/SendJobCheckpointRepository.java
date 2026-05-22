package com.legent.campaign.repository;

import java.util.List;
import java.util.Optional;

import com.legent.campaign.domain.SendJobCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for send job checkpoint operations.
 */
@Repository
public interface SendJobCheckpointRepository extends JpaRepository<SendJobCheckpoint, String> {

    List<SendJobCheckpoint> findByTenantIdAndWorkspaceIdAndJobIdAndDeletedAtIsNullOrderBySequenceNumberDesc(
            String tenantId,
            String workspaceId,
            String jobId);

    Optional<SendJobCheckpoint> findFirstByTenantIdAndWorkspaceIdAndJobIdAndDeletedAtIsNullOrderBySequenceNumberDesc(
            String tenantId,
            String workspaceId,
            String jobId);

    List<SendJobCheckpoint> findByTenantIdAndWorkspaceIdAndJobIdAndCheckpointTypeAndDeletedAtIsNullOrderBySequenceNumberDesc(
            String tenantId,
            String workspaceId,
            String jobId,
            SendJobCheckpoint.CheckpointType checkpointType);

    @Query("""
            SELECT COUNT(c)
            FROM SendJobCheckpoint c
            WHERE c.tenantId = :tenantId
              AND c.workspaceId = :workspaceId
              AND c.jobId = :jobId
              AND c.deletedAt IS NULL
            """)
    long countByTenantIdAndWorkspaceIdAndJobId(@Param("tenantId") String tenantId,
                                               @Param("workspaceId") String workspaceId,
                                               @Param("jobId") String jobId);
}

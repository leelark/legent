package com.legent.campaign.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.legent.campaign.domain.SendJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface SendJobRepository extends JpaRepository<SendJob, String> {
    Page<SendJob> findByTenantIdAndCampaignIdAndDeletedAtIsNull(String tenantId, String campaignId, Pageable pageable);

    Page<SendJob> findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(String tenantId, String workspaceId, String campaignId, Pageable pageable);

    List<SendJob> findByTenantIdAndStatusInAndDeletedAtIsNull(String tenantId, List<SendJob.JobStatus> statuses);

    Optional<SendJob> findFirstByTenantIdAndCampaignIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId, String campaignId);

    Optional<SendJob> findFirstByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId, String workspaceId, String campaignId);

    List<SendJob> findByStatusAndScheduledAtBefore(SendJob.JobStatus status, java.time.Instant now);

    List<SendJob> findByStatusAndScheduledAtBeforeAndDeletedAtIsNullOrderByScheduledAtAscCreatedAtAsc(
            SendJob.JobStatus status,
            java.time.Instant now,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE SendJob j
            SET j.status = :resolvingStatus,
                j.startedAt = :startedAt,
                j.updatedAt = :startedAt
            WHERE j.tenantId = :tenantId
              AND j.workspaceId = :workspaceId
              AND j.id = :jobId
              AND j.status = :pendingStatus
              AND j.scheduledAt <= :dueBefore
              AND j.deletedAt IS NULL
            """)
    int claimDueScheduledJob(@Param("tenantId") String tenantId,
                             @Param("workspaceId") String workspaceId,
                             @Param("jobId") String jobId,
                             @Param("pendingStatus") SendJob.JobStatus pendingStatus,
                             @Param("resolvingStatus") SendJob.JobStatus resolvingStatus,
                             @Param("dueBefore") java.time.Instant dueBefore,
                             @Param("startedAt") java.time.Instant startedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE SendJob j
            SET j.totalSent = COALESCE(j.totalSent, 0) + 1,
                j.updatedAt = :updatedAt
            WHERE j.tenantId = :tenantId
              AND j.workspaceId = :workspaceId
              AND j.id = :jobId
              AND j.deletedAt IS NULL
            """)
    int incrementSentFeedbackCounter(@Param("tenantId") String tenantId,
                                     @Param("workspaceId") String workspaceId,
                                     @Param("jobId") String jobId,
                                     @Param("updatedAt") Instant updatedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE SendJob j
            SET j.totalFailed = COALESCE(j.totalFailed, 0) + 1,
                j.errorMessage = COALESCE(:reason, j.errorMessage),
                j.updatedAt = :updatedAt
            WHERE j.tenantId = :tenantId
              AND j.workspaceId = :workspaceId
              AND j.id = :jobId
              AND j.deletedAt IS NULL
            """)
    int incrementFailedFeedbackCounter(@Param("tenantId") String tenantId,
                                       @Param("workspaceId") String workspaceId,
                                       @Param("jobId") String jobId,
                                       @Param("reason") String reason,
                                       @Param("updatedAt") Instant updatedAt);

    List<SendJob> findByTenantIdAndWorkspaceIdAndStatusAndScheduledAtBefore(String tenantId, String workspaceId, SendJob.JobStatus status, java.time.Instant now);

    Optional<SendJob> findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(String tenantId, String workspaceId, String id);

    List<SendJob> findByTenantIdAndWorkspaceIdAndCampaignIdAndStatusInAndDeletedAtIsNull(
            String tenantId,
            String workspaceId,
            String campaignId,
            List<SendJob.JobStatus> statuses
    );

    Optional<SendJob> findByTenantIdAndWorkspaceIdAndIdempotencyKeyAndDeletedAtIsNull(
            String tenantId,
            String workspaceId,
            String idempotencyKey
    );
}

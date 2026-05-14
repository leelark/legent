package com.legent.campaign.repository;

import java.util.Optional;

import java.util.List;

import com.legent.campaign.domain.SendJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SendJobRepository extends JpaRepository<SendJob, String> {
    Page<SendJob> findByTenantIdAndCampaignIdAndDeletedAtIsNull(String tenantId, String campaignId, Pageable pageable);

    Page<SendJob> findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(String tenantId, String workspaceId, String campaignId, Pageable pageable);

    List<SendJob> findByTenantIdAndStatusInAndDeletedAtIsNull(String tenantId, List<SendJob.JobStatus> statuses);

    Optional<SendJob> findFirstByTenantIdAndCampaignIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId, String campaignId);

    Optional<SendJob> findFirstByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId, String workspaceId, String campaignId);

    List<SendJob> findByStatusAndScheduledAtBefore(SendJob.JobStatus status, java.time.Instant now);

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

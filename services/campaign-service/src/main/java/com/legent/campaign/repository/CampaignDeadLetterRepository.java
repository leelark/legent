package com.legent.campaign.repository;

import java.util.List;
import java.util.Optional;

import com.legent.campaign.domain.CampaignDeadLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignDeadLetterRepository extends JpaRepository<CampaignDeadLetter, String> {
    List<CampaignDeadLetter> findByTenantIdAndWorkspaceIdAndJobIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            String tenantId, String workspaceId, String jobId);

    Optional<CampaignDeadLetter> findByTenantIdAndWorkspaceIdAndJobIdAndIdAndDeletedAtIsNull(
            String tenantId, String workspaceId, String jobId, String id);
}

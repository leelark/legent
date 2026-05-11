package com.legent.campaign.repository;

import com.legent.campaign.domain.CampaignLaunchPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignLaunchPlanRepository extends JpaRepository<CampaignLaunchPlan, String> {

    Optional<CampaignLaunchPlan> findByTenantIdAndWorkspaceIdAndIdempotencyKeyAndDeletedAtIsNull(
            String tenantId,
            String workspaceId,
            String idempotencyKey
    );

    List<CampaignLaunchPlan> findTop10ByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            String tenantId,
            String workspaceId,
            String campaignId
    );
}

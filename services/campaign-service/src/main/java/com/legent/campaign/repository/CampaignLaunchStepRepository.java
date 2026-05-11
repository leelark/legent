package com.legent.campaign.repository;

import com.legent.campaign.domain.CampaignLaunchStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignLaunchStepRepository extends JpaRepository<CampaignLaunchStep, String> {

    List<CampaignLaunchStep> findByTenantIdAndWorkspaceIdAndLaunchPlanIdAndDeletedAtIsNullOrderBySortOrderAsc(
            String tenantId,
            String workspaceId,
            String launchPlanId
    );
}

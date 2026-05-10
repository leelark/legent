package com.legent.campaign.repository;

import java.util.Optional;

import com.legent.campaign.domain.CampaignBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignBudgetRepository extends JpaRepository<CampaignBudget, String> {
    Optional<CampaignBudget> findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(
            String tenantId, String workspaceId, String campaignId);
}

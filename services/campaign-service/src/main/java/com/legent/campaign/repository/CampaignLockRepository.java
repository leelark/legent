package com.legent.campaign.repository;

import java.util.Optional;

import com.legent.campaign.domain.CampaignLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignLockRepository extends JpaRepository<CampaignLock, String> {
    Optional<CampaignLock> findFirstByTenantIdAndWorkspaceIdAndCampaignIdAndStatusAndDeletedAtIsNullOrderByLockedAtDesc(
            String tenantId, String workspaceId, String campaignId, String status);
}

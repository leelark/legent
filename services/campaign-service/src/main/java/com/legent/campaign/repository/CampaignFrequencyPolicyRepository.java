package com.legent.campaign.repository;

import java.util.Optional;

import com.legent.campaign.domain.CampaignFrequencyPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignFrequencyPolicyRepository extends JpaRepository<CampaignFrequencyPolicy, String> {
    Optional<CampaignFrequencyPolicy> findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNull(
            String tenantId, String workspaceId, String campaignId);
}

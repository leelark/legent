package com.legent.campaign.repository;

import java.util.List;
import java.util.Optional;

import com.legent.campaign.domain.CampaignVariantMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignVariantMetricRepository extends JpaRepository<CampaignVariantMetric, String> {
    List<CampaignVariantMetric> findByTenantIdAndWorkspaceIdAndCampaignIdAndExperimentIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            String tenantId, String workspaceId, String campaignId, String experimentId);

    Optional<CampaignVariantMetric> findByTenantIdAndWorkspaceIdAndCampaignIdAndExperimentIdAndVariantIdAndDeletedAtIsNull(
            String tenantId, String workspaceId, String campaignId, String experimentId, String variantId);
}

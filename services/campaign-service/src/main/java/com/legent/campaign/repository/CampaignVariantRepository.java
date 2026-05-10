package com.legent.campaign.repository;

import java.util.List;
import java.util.Optional;

import com.legent.campaign.domain.CampaignVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignVariantRepository extends JpaRepository<CampaignVariant, String> {
    List<CampaignVariant> findByTenantIdAndWorkspaceIdAndExperimentIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            String tenantId, String workspaceId, String experimentId);

    List<CampaignVariant> findByTenantIdAndWorkspaceIdAndExperimentIdAndActiveTrueAndDeletedAtIsNullOrderByCreatedAtAsc(
            String tenantId, String workspaceId, String experimentId);

    Optional<CampaignVariant> findByTenantIdAndWorkspaceIdAndExperimentIdAndIdAndDeletedAtIsNull(
            String tenantId, String workspaceId, String experimentId, String id);
}

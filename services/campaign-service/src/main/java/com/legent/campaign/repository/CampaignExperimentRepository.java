package com.legent.campaign.repository;

import java.util.List;
import java.util.Optional;

import com.legent.campaign.domain.CampaignExperiment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignExperimentRepository extends JpaRepository<CampaignExperiment, String> {
    List<CampaignExperiment> findByTenantIdAndWorkspaceIdAndCampaignIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            String tenantId, String workspaceId, String campaignId);

    Optional<CampaignExperiment> findByTenantIdAndWorkspaceIdAndCampaignIdAndIdAndDeletedAtIsNull(
            String tenantId, String workspaceId, String campaignId, String id);

    Optional<CampaignExperiment> findFirstByTenantIdAndWorkspaceIdAndCampaignIdAndStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
            String tenantId,
            String workspaceId,
            String campaignId,
            List<CampaignExperiment.ExperimentStatus> statuses);

    List<CampaignExperiment> findByStatusAndDeletedAtIsNull(CampaignExperiment.ExperimentStatus status);
}

package com.legent.campaign.repository;

import java.util.List;
import java.util.Optional;

import com.legent.campaign.domain.CampaignExperiment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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

    Slice<CampaignExperiment> findByStatusAndDeletedAtIsNullOrderByIdAsc(
            CampaignExperiment.ExperimentStatus status,
            Pageable pageable);

    Slice<CampaignExperiment> findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
            CampaignExperiment.ExperimentStatus status,
            String id,
            Pageable pageable);
}

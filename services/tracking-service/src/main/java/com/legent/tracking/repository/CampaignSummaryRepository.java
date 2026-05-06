package com.legent.tracking.repository;

import java.util.Optional;

import com.legent.tracking.domain.CampaignSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface CampaignSummaryRepository extends JpaRepository<CampaignSummary, String> {
    Optional<CampaignSummary> findByTenantIdAndWorkspaceIdAndCampaignId(String tenantId, String workspaceId, String campaignId);
    java.util.List<CampaignSummary> findAllByTenantIdAndWorkspaceId(String tenantId, String workspaceId);
}

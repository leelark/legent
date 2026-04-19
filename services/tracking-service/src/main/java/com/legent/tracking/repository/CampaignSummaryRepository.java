package com.legent.tracking.repository;

import java.util.Optional;

import com.legent.tracking.domain.CampaignSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface CampaignSummaryRepository extends JpaRepository<CampaignSummary, String> {
    Optional<CampaignSummary> findByTenantIdAndCampaignId(String tenantId, String campaignId);
}

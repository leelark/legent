package com.legent.campaign.repository;

import java.util.List;
import java.util.Optional;

import com.legent.campaign.domain.CampaignApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for campaign approval operations.
 */
@Repository
public interface CampaignApprovalRepository extends JpaRepository<CampaignApproval, String> {

    List<CampaignApproval> findByTenantIdAndCampaignIdOrderByRequestedAtDesc(String tenantId, String campaignId);

    @Query("SELECT a FROM CampaignApproval a WHERE a.tenantId = :tid AND a.campaignId = :campaignId AND a.status = 'PENDING'")
    Optional<CampaignApproval> findPendingApproval(@Param("tid") String tenantId, @Param("campaignId") String campaignId);

    List<CampaignApproval> findByTenantIdAndStatus(String tenantId, CampaignApproval.ApprovalStatus status);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM CampaignApproval a " +
           "WHERE a.tenantId = :tid AND a.campaignId = :campaignId AND a.status = 'PENDING'")
    boolean hasPendingApproval(@Param("tid") String tenantId, @Param("campaignId") String campaignId);
}

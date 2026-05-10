package com.legent.campaign.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import com.legent.campaign.domain.CampaignSendLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignSendLedgerRepository extends JpaRepository<CampaignSendLedger, String> {
    Optional<CampaignSendLedger> findByTenantIdAndWorkspaceIdAndMessageIdAndDeletedAtIsNull(
            String tenantId, String workspaceId, String messageId);

    @Query("""
        SELECT COUNT(l) FROM CampaignSendLedger l
        WHERE l.tenantId = :tenantId
          AND l.workspaceId = :workspaceId
          AND LOWER(l.email) = LOWER(:email)
          AND l.createdAt >= :since
          AND l.sendState IN :states
          AND l.deletedAt IS NULL
    """)
    long countRecipientTouchesSince(@Param("tenantId") String tenantId,
                                    @Param("workspaceId") String workspaceId,
                                    @Param("email") String email,
                                    @Param("since") Instant since,
                                    @Param("states") Collection<CampaignSendLedger.SendState> states);

    @Query("""
        SELECT COUNT(l) FROM CampaignSendLedger l
        WHERE l.tenantId = :tenantId
          AND l.workspaceId = :workspaceId
          AND l.campaignId = :campaignId
          AND l.sendState = :state
          AND l.deletedAt IS NULL
    """)
    long countByState(@Param("tenantId") String tenantId,
                      @Param("workspaceId") String workspaceId,
                      @Param("campaignId") String campaignId,
                      @Param("state") CampaignSendLedger.SendState state);

    @Query("""
        SELECT COUNT(l) FROM CampaignSendLedger l
        WHERE l.tenantId = :tenantId
          AND l.workspaceId = :workspaceId
          AND l.campaignId = :campaignId
          AND l.jobId = :jobId
          AND l.sendState = :state
          AND l.deletedAt IS NULL
    """)
    long countJobByState(@Param("tenantId") String tenantId,
                         @Param("workspaceId") String workspaceId,
                         @Param("campaignId") String campaignId,
                         @Param("jobId") String jobId,
                         @Param("state") CampaignSendLedger.SendState state);
}

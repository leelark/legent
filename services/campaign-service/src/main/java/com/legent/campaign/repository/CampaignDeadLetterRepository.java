package com.legent.campaign.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.legent.campaign.domain.CampaignDeadLetter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignDeadLetterRepository extends JpaRepository<CampaignDeadLetter, String> {
    List<CampaignDeadLetter> findByTenantIdAndWorkspaceIdAndJobIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            String tenantId, String workspaceId, String jobId, Pageable pageable);

    Optional<CampaignDeadLetter> findByTenantIdAndWorkspaceIdAndJobIdAndIdAndDeletedAtIsNull(
            String tenantId, String workspaceId, String jobId, String id);

    long countByStatusAndDeletedAtIsNull(String status);

    @org.springframework.data.jpa.repository.Query("""
            SELECT MIN(letter.createdAt)
            FROM CampaignDeadLetter letter
            WHERE letter.status = :status
              AND letter.deletedAt IS NULL
            """)
    Optional<Instant> findOldestCreatedAtByStatus(@org.springframework.data.repository.query.Param("status") String status);

    @org.springframework.data.jpa.repository.Query("""
            SELECT COUNT(letter)
            FROM CampaignDeadLetter letter
            WHERE letter.status = :status
              AND letter.deletedAt IS NULL
            GROUP BY letter.jobId
            ORDER BY COUNT(letter) DESC
            """)
    List<Long> findOpenCountsByJob(@org.springframework.data.repository.query.Param("status") String status,
                                   Pageable pageable);
}

package com.legent.deliverability.repository;

import java.util.List;
import java.util.Optional;

import com.legent.deliverability.domain.SuppressionList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface SuppressionListRepository extends JpaRepository<SuppressionList, String> {
    List<SuppressionList> findByTenantIdAndWorkspaceId(String tenantId, String workspaceId);
    Optional<SuppressionList> findByTenantIdAndWorkspaceIdAndEmail(String tenantId, String workspaceId, String email);
    @Query("""
            SELECT s.email
            FROM SuppressionList s
            WHERE s.tenantId = :tenantId
              AND s.workspaceId = :workspaceId
              AND LOWER(TRIM(s.email)) IN :normalizedEmails
              AND (s.expiresAt IS NULL OR s.expiresAt > CURRENT_TIMESTAMP)
            """)
    List<String> findActiveEmailsByTenantIdAndWorkspaceIdAndNormalizedEmailIn(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("normalizedEmails") List<String> normalizedEmails);
    long countByTenantIdAndWorkspaceIdAndReason(String tenantId, String workspaceId, String reason);
}

package com.legent.deliverability.repository;

import java.util.List;
import java.util.Optional;

import com.legent.deliverability.domain.SuppressionList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SuppressionListRepository extends JpaRepository<SuppressionList, String> {
    List<SuppressionList> findByTenantIdAndWorkspaceId(String tenantId, String workspaceId);
    Optional<SuppressionList> findByTenantIdAndWorkspaceIdAndEmail(String tenantId, String workspaceId, String email);
    long countByTenantIdAndWorkspaceIdAndReason(String tenantId, String workspaceId, String reason);
}

package com.legent.content.repository;

import com.legent.content.domain.SendGovernancePolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SendGovernancePolicyRepository extends JpaRepository<SendGovernancePolicy, String> {
    Page<SendGovernancePolicy> findByTenantIdAndWorkspaceIdAndDeletedAtIsNull(String tenantId, String workspaceId, Pageable pageable);

    Optional<SendGovernancePolicy> findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(String id, String tenantId, String workspaceId);

    boolean existsByTenantIdAndWorkspaceIdAndPolicyKeyIgnoreCaseAndDeletedAtIsNull(String tenantId, String workspaceId, String policyKey);
}

package com.legent.identity.repository;

import com.legent.identity.domain.AccountMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountMembershipRepository extends JpaRepository<AccountMembership, String> {
    List<AccountMembership> findByAccountIdAndStatus(String accountId, String status);
    Optional<AccountMembership> findByAccountIdAndTenantId(String accountId, String tenantId);
    List<AccountMembership> findAllByAccountIdAndTenantIdAndStatus(String accountId, String tenantId, String status);
    Optional<AccountMembership> findByAccountIdAndTenantIdAndWorkspaceIdAndStatus(
            String accountId,
            String tenantId,
            String workspaceId,
            String status);
    Optional<AccountMembership> findByUserIdAndTenantId(String userId, String tenantId);
    List<AccountMembership> findAllByUserIdAndTenantId(String userId, String tenantId);
}

package com.legent.delivery.repository;

import com.legent.delivery.domain.WarmupState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarmupStateRepository extends JpaRepository<WarmupState, String> {
    Optional<WarmupState> findByTenantIdAndWorkspaceIdAndSenderDomainAndProviderId(
            String tenantId, String workspaceId, String senderDomain, String providerId);

    List<WarmupState> findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(String tenantId, String workspaceId);
}

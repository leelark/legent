package com.legent.delivery.repository;

import com.legent.delivery.domain.SendRateState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SendRateStateRepository extends JpaRepository<SendRateState, String> {
    Optional<SendRateState> findByTenantIdAndWorkspaceIdAndRateLimitKey(String tenantId, String workspaceId, String rateLimitKey);
    List<SendRateState> findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(String tenantId, String workspaceId);
}

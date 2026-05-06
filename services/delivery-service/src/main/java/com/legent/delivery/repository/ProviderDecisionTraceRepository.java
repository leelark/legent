package com.legent.delivery.repository;

import com.legent.delivery.domain.ProviderDecisionTrace;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProviderDecisionTraceRepository extends JpaRepository<ProviderDecisionTrace, String> {
    List<ProviderDecisionTrace> findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(String tenantId, String workspaceId, Pageable pageable);
}

package com.legent.delivery.repository;

import com.legent.delivery.domain.ProviderFailoverTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderFailoverTestRepository extends JpaRepository<ProviderFailoverTest, String> {
    List<ProviderFailoverTest> findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(String tenantId, String workspaceId, Pageable pageable);
}

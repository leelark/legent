package com.legent.deliverability.repository;

import java.util.List;
import java.util.Optional;

import com.legent.deliverability.domain.DomainReputation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface DomainReputationRepository extends JpaRepository<DomainReputation, String> {
    Optional<DomainReputation> findByTenantIdAndWorkspaceIdAndDomainId(String tenantId, String workspaceId, String domainId);
    List<DomainReputation> findByTenantIdAndWorkspaceIdOrderByCalculatedAtDesc(String tenantId, String workspaceId);
}

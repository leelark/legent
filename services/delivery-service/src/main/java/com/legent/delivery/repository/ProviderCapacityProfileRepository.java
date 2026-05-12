package com.legent.delivery.repository;

import com.legent.delivery.domain.ProviderCapacityProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderCapacityProfileRepository extends JpaRepository<ProviderCapacityProfile, String> {
    List<ProviderCapacityProfile> findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(String tenantId, String workspaceId);

    Optional<ProviderCapacityProfile> findByTenantIdAndWorkspaceIdAndProviderIdAndSenderDomainAndIspDomain(
            String tenantId,
            String workspaceId,
            String providerId,
            String senderDomain,
            String ispDomain);
}

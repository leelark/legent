package com.legent.tracking.repository;

import java.util.Optional;

import com.legent.tracking.domain.SubscriberSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SubscriberSummaryRepository extends JpaRepository<SubscriberSummary, String> {
    Optional<SubscriberSummary> findByTenantIdAndWorkspaceIdAndSubscriberId(String tenantId, String workspaceId, String subscriberId);
}

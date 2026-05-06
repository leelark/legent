package com.legent.tracking.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.legent.tracking.domain.RawEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface RawEventRepository extends JpaRepository<RawEvent, String> {
    List<RawEvent> findByTenantIdAndWorkspaceIdAndCampaignIdAndEventType(String tenantId, String workspaceId, String campaignId, String eventType);

    /**
     * Checks if an event already exists for the given tenant, event type, message and subscriber.
     * Used for duplicate detection within a time window.
     */
    Optional<RawEvent> findTopByTenantIdAndWorkspaceIdAndEventTypeAndMessageIdAndSubscriberIdAndTimestampAfter(
            String tenantId, String workspaceId, String eventType, String messageId, String subscriberId, Instant since);

    Optional<RawEvent> findTopByTenantIdAndMessageIdAndWorkspaceIdIsNotNullOrderByTimestampDesc(String tenantId, String messageId);
}

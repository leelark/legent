package com.legent.delivery.repository;

import com.legent.delivery.domain.DeliveryFeedbackOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface DeliveryFeedbackOutboxEventRepository extends JpaRepository<DeliveryFeedbackOutboxEvent, String> {

    boolean existsByTenantIdAndWorkspaceIdAndEventTypeAndTransitionKeyAndDeletedAtIsNull(
            String tenantId,
            String workspaceId,
            String eventType,
            String transitionKey);

    List<DeliveryFeedbackOutboxEvent> findTop100ByStatusInAndNextAttemptAtLessThanEqualAndDeletedAtIsNullOrderByCreatedAtAsc(
            Collection<String> statuses,
            Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE DeliveryFeedbackOutboxEvent e
               SET e.status = :publishingStatus,
                   e.attempts = e.attempts + 1,
                   e.lastAttemptAt = :now,
                   e.lastError = null,
                   e.nextAttemptAt = :leaseUntil
             WHERE e.id = :id
               AND e.deletedAt IS NULL
               AND e.status IN (:claimableStatuses)
               AND e.nextAttemptAt <= :now
            """)
    int claimReadyForPublish(@Param("id") String id,
                             @Param("claimableStatuses") Collection<String> claimableStatuses,
                             @Param("now") Instant now,
                             @Param("publishingStatus") String publishingStatus,
                             @Param("leaseUntil") Instant leaseUntil);
}

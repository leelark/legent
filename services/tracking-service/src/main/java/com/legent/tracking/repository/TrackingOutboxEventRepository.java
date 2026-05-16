package com.legent.tracking.repository;

import com.legent.tracking.domain.TrackingOutboxEvent;
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
public interface TrackingOutboxEventRepository extends JpaRepository<TrackingOutboxEvent, String> {
    List<TrackingOutboxEvent> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<String> statuses, Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE TrackingOutboxEvent e
               SET e.status = :publishingStatus,
                   e.attempts = e.attempts + 1,
                   e.lastError = null,
                   e.nextAttemptAt = :leaseUntil
             WHERE e.id = :id
               AND e.status IN (:claimableStatuses)
               AND e.nextAttemptAt <= :now
            """)
    int claimReadyForPublish(@Param("id") String id,
                             @Param("claimableStatuses") Collection<String> claimableStatuses,
                             @Param("now") Instant now,
                             @Param("publishingStatus") String publishingStatus,
                             @Param("leaseUntil") Instant leaseUntil);
}

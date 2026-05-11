package com.legent.tracking.repository;

import com.legent.tracking.domain.TrackingOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface TrackingOutboxEventRepository extends JpaRepository<TrackingOutboxEvent, String> {
    List<TrackingOutboxEvent> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<String> statuses, Instant now);
}

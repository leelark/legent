package com.legent.audience.repository;

import com.legent.audience.domain.SegmentMembership;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SegmentMembershipRepository extends JpaRepository<SegmentMembership, String> {

    Page<SegmentMembership> findBySegmentId(String segmentId, Pageable pageable);

    @Query("SELECT COUNT(m) FROM SegmentMembership m WHERE m.segmentId = :segId")
    long countBySegment(@Param("segId") String segmentId);

    @Modifying
    @Query("DELETE FROM SegmentMembership m WHERE m.segmentId = :segId")
    void deleteAllBySegmentId(@Param("segId") String segmentId);

    boolean existsBySegmentIdAndSubscriberId(String segmentId, String subscriberId);
}

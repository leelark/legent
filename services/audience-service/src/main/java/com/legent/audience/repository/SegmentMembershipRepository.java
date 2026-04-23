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

    Page<SegmentMembership> findByTenantIdAndSegmentId(String tenantId, String segmentId, Pageable pageable);
    java.util.List<SegmentMembership> findByTenantIdAndSegmentId(String tenantId, String segmentId);

    @Query("SELECT COUNT(m) FROM SegmentMembership m WHERE m.tenantId = :tid AND m.segmentId = :segId")
    long countByTenantAndSegment(@Param("tid") String tenantId, @Param("segId") String segmentId);

    @Modifying
    @Query("DELETE FROM SegmentMembership m WHERE m.tenantId = :tid AND m.segmentId = :segId")
    void deleteAllByTenantIdAndSegmentId(@Param("tid") String tenantId, @Param("segId") String segmentId);

    boolean existsByTenantIdAndSegmentIdAndSubscriberId(String tenantId, String segmentId, String subscriberId);
}

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

    Page<SegmentMembership> findByTenantIdAndWorkspaceIdAndSegmentId(String tenantId, String workspaceId, String segmentId, Pageable pageable);
    java.util.List<SegmentMembership> findByTenantIdAndWorkspaceIdAndSegmentId(String tenantId, String workspaceId, String segmentId);

    @Query("SELECT COUNT(m) FROM SegmentMembership m WHERE m.tenantId = :tid AND m.workspaceId = :wid AND m.segmentId = :segId")
    long countByTenantAndWorkspaceAndSegment(@Param("tid") String tenantId, @Param("wid") String workspaceId, @Param("segId") String segmentId);

    @Modifying
    @Query("DELETE FROM SegmentMembership m WHERE m.tenantId = :tid AND m.workspaceId = :wid AND m.segmentId = :segId")
    void deleteAllByTenantIdAndWorkspaceIdAndSegmentId(@Param("tid") String tenantId, @Param("wid") String workspaceId, @Param("segId") String segmentId);

    boolean existsByTenantIdAndWorkspaceIdAndSegmentIdAndSubscriberId(String tenantId, String workspaceId, String segmentId, String subscriberId);

    @Modifying
    @Query("""
        UPDATE SegmentMembership m
        SET m.subscriberId = :targetSubscriberId
        WHERE m.tenantId = :tenantId
          AND m.workspaceId = :workspaceId
          AND m.subscriberId = :sourceSubscriberId
    """)
    void reassignSubscriber(@Param("tenantId") String tenantId,
                            @Param("workspaceId") String workspaceId,
                            @Param("sourceSubscriberId") String sourceSubscriberId,
                            @Param("targetSubscriberId") String targetSubscriberId);
}

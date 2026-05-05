package com.legent.audience.repository;

import java.util.Optional;

import java.util.List;

import com.legent.audience.domain.Segment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface SegmentRepository extends JpaRepository<Segment, String> {

    @Query("SELECT s FROM Segment s WHERE s.tenantId = :tid AND s.deletedAt IS NULL")
    Page<Segment> findAllByTenant(@Param("tid") String tenantId, Pageable pageable);

    @Query("SELECT s FROM Segment s WHERE s.tenantId = :tid AND s.workspaceId = :wid AND s.deletedAt IS NULL")
    Page<Segment> findAllByTenantAndWorkspace(@Param("tid") String tenantId, @Param("wid") String workspaceId, Pageable pageable);

    Optional<Segment> findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(String tenantId, String workspaceId, String id);

    boolean existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(String tenantId, String workspaceId, String name);

    @Query("SELECT s FROM Segment s WHERE s.scheduleEnabled = true AND s.status IN ('ACTIVE','DRAFT') AND s.deletedAt IS NULL")
    List<Segment> findScheduledSegments();

    @Query("SELECT COUNT(s) FROM Segment s WHERE s.tenantId = :tid AND s.workspaceId = :wid AND s.deletedAt IS NULL")
    long countByTenantAndWorkspace(@Param("tid") String tenantId, @Param("wid") String workspaceId);
}

package com.legent.audience.repository;

import java.util.List;

import com.legent.audience.domain.Suppression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface SuppressionRepository extends JpaRepository<Suppression, String> {

    @Query("SELECT s FROM Suppression s WHERE s.tenantId = :tid AND s.deletedAt IS NULL")
    Page<Suppression> findAllByTenant(@Param("tid") String tenantId, Pageable pageable);

    @Query("SELECT s FROM Suppression s WHERE s.tenantId = :tid AND s.workspaceId = :wid AND s.deletedAt IS NULL")
    Page<Suppression> findAllByTenantAndWorkspace(@Param("tid") String tenantId, @Param("wid") String workspaceId, Pageable pageable);

    @Query("""
        SELECT s FROM Suppression s
        WHERE s.tenantId = :tid AND s.workspaceId = :wid AND LOWER(s.email) = LOWER(:email) AND s.deletedAt IS NULL
          AND (s.expiresAt IS NULL OR s.expiresAt > CURRENT_TIMESTAMP)
    """)
    List<Suppression> findActiveSuppression(@Param("tid") String tenantId, @Param("wid") String workspaceId, @Param("email") String email);

    boolean existsByTenantIdAndWorkspaceIdAndEmailAndSuppressionTypeAndDeletedAtIsNull(
            String tenantId, String workspaceId, String email, Suppression.SuppressionType type);

    java.util.Optional<Suppression> findByTenantIdAndWorkspaceIdAndEmailAndSuppressionTypeAndDeletedAtIsNull(
            String tenantId, String workspaceId, String email, Suppression.SuppressionType type);

    @Query("SELECT COUNT(s) FROM Suppression s WHERE s.tenantId = :tid AND s.workspaceId = :wid AND s.deletedAt IS NULL")
    long countByTenantAndWorkspace(@Param("tid") String tenantId, @Param("wid") String workspaceId);
}

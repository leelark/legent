package com.legent.foundation.repository;

import java.util.List;

import com.legent.foundation.domain.TenantAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository for tenant audit log operations.
 */
@Repository
public interface TenantAuditLogRepository extends JpaRepository<TenantAuditLog, String> {

    Page<TenantAuditLog> findByTenantIdOrderByPerformedAtDesc(String tenantId, Pageable pageable);

    List<TenantAuditLog> findByTenantIdAndEntityTypeOrderByPerformedAtDesc(String tenantId, String entityType);

    @Query("SELECT a FROM TenantAuditLog a WHERE a.tenantId = :tenantId AND a.action = :action ORDER BY a.performedAt DESC")
    List<TenantAuditLog> findByTenantIdAndAction(String tenantId, String action);

    Page<TenantAuditLog> findByEntityTypeAndEntityIdOrderByPerformedAtDesc(String entityType, String entityId, Pageable pageable);
}

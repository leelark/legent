package com.legent.foundation.repository;

import com.legent.foundation.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    Page<AuditLog> findByTenantId(String tenantId, Pageable pageable);
    Page<AuditLog> findByTenantIdAndResourceType(String tenantId, String resourceType, Pageable pageable);
}

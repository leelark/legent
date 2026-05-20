package com.legent.delivery.repository;

import java.util.List;
import java.util.Optional;

import com.legent.delivery.domain.SmtpProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;


@Repository
public interface SmtpProviderRepository extends JpaRepository<SmtpProvider, String> {
    List<SmtpProvider> findByTenantIdAndWorkspaceIdAndIsActiveTrueAndDeletedAtIsNullOrderByPriorityAsc(
            String tenantId,
            String workspaceId);

    List<SmtpProvider> findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByPriorityAsc(String tenantId, String workspaceId);

    Optional<SmtpProvider> findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
            String id,
            String tenantId,
            String workspaceId);

    @Query("SELECT p FROM SmtpProvider p WHERE p.isActive = true AND p.deletedAt IS NULL AND p.workspaceId IS NOT NULL")
    List<SmtpProvider> findAllActiveProviders();
}

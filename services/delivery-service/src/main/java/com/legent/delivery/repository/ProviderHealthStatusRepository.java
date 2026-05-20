package com.legent.delivery.repository;

import java.util.List;
import java.util.Optional;

import com.legent.delivery.domain.ProviderHealthStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for provider health status operations.
 */
@Repository
public interface ProviderHealthStatusRepository extends JpaRepository<ProviderHealthStatus, String> {

    Optional<ProviderHealthStatus> findByTenantIdAndWorkspaceIdAndProviderId(
            String tenantId,
            String workspaceId,
            String providerId);

    List<ProviderHealthStatus> findByTenantIdAndWorkspaceId(String tenantId, String workspaceId);

    @Query("SELECT h FROM ProviderHealthStatus h WHERE h.tenantId = :tenantId AND h.workspaceId = :workspaceId AND h.currentStatus != 'HEALTHY'")
    List<ProviderHealthStatus> findUnhealthyProviders(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId);

    @Query("SELECT h FROM ProviderHealthStatus h WHERE h.tenantId = :tenantId AND h.workspaceId = :workspaceId AND h.circuitBreakerOpen = true")
    List<ProviderHealthStatus> findProvidersWithOpenCircuit(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId);

    @Query("SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END FROM ProviderHealthStatus h " +
           "WHERE h.tenantId = :tenantId AND h.workspaceId = :workspaceId AND h.providerId = :providerId " +
           "AND h.currentStatus = 'HEALTHY' AND h.circuitBreakerOpen = false")
    boolean isProviderHealthy(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("providerId") String providerId);
}

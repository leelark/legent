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

    Optional<ProviderHealthStatus> findByProviderId(String providerId);

    List<ProviderHealthStatus> findByTenantId(String tenantId);

    @Query("SELECT h FROM ProviderHealthStatus h WHERE h.tenantId = :tid AND h.currentStatus != 'HEALTHY'")
    List<ProviderHealthStatus> findUnhealthyProviders(@Param("tid") String tenantId);

    @Query("SELECT h FROM ProviderHealthStatus h WHERE h.tenantId = :tid AND h.circuitBreakerOpen = true")
    List<ProviderHealthStatus> findProvidersWithOpenCircuit(@Param("tid") String tenantId);

    @Query("SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END FROM ProviderHealthStatus h " +
           "WHERE h.providerId = :providerId AND h.currentStatus = 'HEALTHY' AND h.circuitBreakerOpen = false")
    boolean isProviderHealthy(@Param("providerId") String providerId);
}

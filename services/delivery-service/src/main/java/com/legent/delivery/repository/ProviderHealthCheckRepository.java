package com.legent.delivery.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.legent.delivery.domain.ProviderHealthCheck;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for provider health check operations.
 */
@Repository
public interface ProviderHealthCheckRepository extends JpaRepository<ProviderHealthCheck, String> {

    List<ProviderHealthCheck> findByTenantIdAndWorkspaceIdAndProviderIdOrderByCheckTimestampDescIdDesc(
            String tenantId,
            String workspaceId,
            String providerId,
            Pageable pageable);

    Optional<ProviderHealthCheck> findFirstByTenantIdAndWorkspaceIdAndProviderIdOrderByCheckTimestampDesc(
            String tenantId,
            String workspaceId,
            String providerId);

    @Query("SELECT h FROM ProviderHealthCheck h WHERE h.tenantId = :tenantId AND h.workspaceId = :workspaceId AND h.checkTimestamp > :since ORDER BY h.checkTimestamp DESC")
    List<ProviderHealthCheck> findRecentChecks(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("since") Instant since);

    @Query("SELECT AVG(h.responseTimeMs) FROM ProviderHealthCheck h WHERE h.tenantId = :tenantId AND h.workspaceId = :workspaceId AND h.providerId = :providerId AND h.checkTimestamp > :since")
    Double calculateAverageResponseTime(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("providerId") String providerId,
            @Param("since") Instant since);

    @Query("SELECT COUNT(h) FROM ProviderHealthCheck h WHERE h.tenantId = :tenantId AND h.workspaceId = :workspaceId AND h.providerId = :providerId AND h.status = 'HEALTHY' AND h.checkTimestamp > :since")
    Long countHealthyChecks(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("providerId") String providerId,
            @Param("since") Instant since);

    @Query("SELECT COUNT(h) FROM ProviderHealthCheck h WHERE h.tenantId = :tenantId AND h.workspaceId = :workspaceId AND h.providerId = :providerId AND h.checkTimestamp > :since")
    Long countTotalChecks(
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId,
            @Param("providerId") String providerId,
            @Param("since") Instant since);
}

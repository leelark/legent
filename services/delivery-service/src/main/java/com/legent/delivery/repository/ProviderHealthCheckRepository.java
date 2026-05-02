package com.legent.delivery.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.legent.delivery.domain.ProviderHealthCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for provider health check operations.
 */
@Repository
public interface ProviderHealthCheckRepository extends JpaRepository<ProviderHealthCheck, String> {

    List<ProviderHealthCheck> findByProviderIdOrderByCheckTimestampDesc(String providerId);

    @Query("SELECT h FROM ProviderHealthCheck h WHERE h.providerId = :providerId ORDER BY h.checkTimestamp DESC LIMIT 1")
    Optional<ProviderHealthCheck> findLatestCheck(@Param("providerId") String providerId);

    @Query("SELECT h FROM ProviderHealthCheck h WHERE h.tenantId = :tid AND h.checkTimestamp > :since ORDER BY h.checkTimestamp DESC")
    List<ProviderHealthCheck> findRecentChecks(@Param("tid") String tenantId, @Param("since") Instant since);

    @Query("SELECT AVG(h.responseTimeMs) FROM ProviderHealthCheck h WHERE h.providerId = :providerId AND h.checkTimestamp > :since")
    Double calculateAverageResponseTime(@Param("providerId") String providerId, @Param("since") Instant since);

    @Query("SELECT COUNT(h) FROM ProviderHealthCheck h WHERE h.providerId = :providerId AND h.status = 'HEALTHY' AND h.checkTimestamp > :since")
    Long countHealthyChecks(@Param("providerId") String providerId, @Param("since") Instant since);

    @Query("SELECT COUNT(h) FROM ProviderHealthCheck h WHERE h.providerId = :providerId AND h.checkTimestamp > :since")
    Long countTotalChecks(@Param("providerId") String providerId, @Param("since") Instant since);
}

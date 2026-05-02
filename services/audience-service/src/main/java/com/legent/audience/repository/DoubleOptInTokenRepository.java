package com.legent.audience.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.legent.audience.domain.DoubleOptInToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for double opt-in token operations.
 */
@Repository
public interface DoubleOptInTokenRepository extends JpaRepository<DoubleOptInToken, String> {

    Optional<DoubleOptInToken> findByTokenHash(String tokenHash);

    List<DoubleOptInToken> findByTenantIdAndSubscriberId(String tenantId, String subscriberId);

    @Query("SELECT t FROM DoubleOptInToken t WHERE t.tenantId = :tenantId AND t.status = 'PENDING' AND t.expiresAt < :now")
    List<DoubleOptInToken> findExpiredTokens(@Param("tenantId") String tenantId, @Param("now") Instant now);

    @Query("SELECT t FROM DoubleOptInToken t WHERE t.tenantId = :tenantId AND t.subscriberId = :subscriberId AND t.status = 'PENDING'")
    Optional<DoubleOptInToken> findPendingTokenForSubscriber(@Param("tenantId") String tenantId,
                                                              @Param("subscriberId") String subscriberId);

    @Modifying
    @Transactional
    @Query("UPDATE DoubleOptInToken t SET t.status = 'EXPIRED' WHERE t.tenantId = :tenantId AND t.expiresAt < :now AND t.status = 'PENDING'")
    int markExpiredTokens(@Param("tenantId") String tenantId, @Param("now") Instant now);

    long countByTenantIdAndStatus(String tenantId, DoubleOptInToken.TokenStatus status);
}

package com.legent.identity.repository;

import com.legent.identity.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for refresh token operations.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /**
     * Finds a refresh token by its hash.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Finds all refresh tokens for a specific user.
     */
    List<RefreshToken> findByUserIdAndTenantId(String userId, String tenantId);

    /**
     * Deletes all expired tokens to clean up the database.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < ?1 OR rt.isRevoked = true")
    int deleteAllExpiredOrRevoked(Instant now);

    /**
     * Revokes all tokens for a specific user (used on logout or security events).
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.userId = ?1 AND rt.tenantId = ?2")
    int revokeAllUserTokens(String userId, String tenantId);
}

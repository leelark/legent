package com.legent.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Refresh token entity for secure token refresh mechanism.
 * Refresh tokens are long-lived tokens used to obtain new access tokens
 * when the current access token expires.
 */
@Entity
@Table(name = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "is_revoked", nullable = false)
    @Builder.Default
    private boolean isRevoked = false;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip_address")
    private String ipAddress;

    /**
     * Checks if the refresh token has expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if the refresh token is valid (not revoked and not expired).
     */
    public boolean isValid() {
        return !isRevoked && !isExpired();
    }
}

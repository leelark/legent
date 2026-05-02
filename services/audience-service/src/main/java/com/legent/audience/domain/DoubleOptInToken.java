package com.legent.audience.domain;

import java.time.Instant;

import com.legent.common.model.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Double opt-in token for email confirmation.
 * Tracks pending and confirmed double opt-in requests.
 */
@Entity
@Table(name = "double_optin_tokens")
@Getter
@Setter
@NoArgsConstructor
public class DoubleOptInToken extends TenantAwareEntity {

    @Column(name = "subscriber_id", nullable = false, length = 36)
    private String subscriberId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TokenStatus status = TokenStatus.PENDING;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    public enum TokenStatus {
        PENDING, CONFIRMED, EXPIRED, REVOKED
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return status == TokenStatus.PENDING && !isExpired();
    }
}

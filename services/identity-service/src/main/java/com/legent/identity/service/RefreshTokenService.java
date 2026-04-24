package com.legent.identity.service;

import com.legent.identity.domain.RefreshToken;
import com.legent.identity.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing refresh tokens.
 * Refresh tokens are long-lived tokens used to obtain new access tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${legent.security.refresh-token.ttl-days:30}")
    private long refreshTokenTtlDays;

    @Value("${legent.security.refresh-token.max-per-user:5}")
    private int maxTokensPerUser;

    /**
     * Creates a new refresh token for a user.
     * Stores only the SHA-256 hash of the token for security.
     */
    @Transactional
    public String createRefreshToken(String userId, String tenantId, String userAgent, String ipAddress) {
        // Generate a cryptographically secure random token
        String rawToken = generateSecureToken();

        // Hash the token for storage (we never store the raw token)
        String tokenHash = hashToken(rawToken);

        // Clean up old tokens if user has too many
        cleanupOldTokens(userId, tenantId);

        // Create and save the refresh token entity
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(tokenHash)
                .userId(userId)
                .tenantId(tenantId)
                .expiresAt(Instant.now().plus(Duration.ofDays(refreshTokenTtlDays)))
                .createdAt(Instant.now())
                .isRevoked(false)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .build();

        refreshTokenRepository.save(refreshToken);
        log.debug("Created refresh token for user {} in tenant {}", userId, tenantId);

        // Return the raw token (this is the only time it's available)
        return rawToken;
    }

    /**
     * Validates a refresh token and returns the associated user info if valid.
     */
    @Transactional(readOnly = true)
    public Optional<TokenValidationResult> validateRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        String tokenHash = hashToken(rawToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            log.warn("Refresh token not found (possible hash collision or invalid token)");
            return Optional.empty();
        }

        RefreshToken refreshToken = tokenOpt.get();

        if (refreshToken.isRevoked()) {
            log.warn("Attempted use of revoked refresh token for user {}", refreshToken.getUserId());
            return Optional.empty();
        }

        if (refreshToken.isExpired()) {
            log.warn("Attempted use of expired refresh token for user {}", refreshToken.getUserId());
            return Optional.empty();
        }

        // Update last used timestamp
        refreshToken.setLastUsedAt(Instant.now());
        refreshTokenRepository.save(refreshToken);

        return Optional.of(new TokenValidationResult(
                refreshToken.getUserId(),
                refreshToken.getTenantId()
        ));
    }

    /**
     * Revokes a specific refresh token.
     */
    @Transactional
    public void revokeToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("Revoked refresh token for user {}", token.getUserId());
        });
    }

    /**
     * Revokes all refresh tokens for a user (e.g., on logout from all devices).
     */
    @Transactional
    public void revokeAllUserTokens(String userId, String tenantId) {
        int count = refreshTokenRepository.revokeAllUserTokens(userId, tenantId);
        log.info("Revoked {} refresh tokens for user {} in tenant {}", count, userId, tenantId);
    }

    /**
     * Cleans up old tokens when a user has too many.
     */
    private void cleanupOldTokens(String userId, String tenantId) {
        var tokens = refreshTokenRepository.findByUserIdAndTenantId(userId, tenantId);
        if (tokens.size() >= maxTokensPerUser) {
            // Remove oldest tokens
            tokens.stream()
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .limit(tokens.size() - maxTokensPerUser + 1)
                    .forEach(token -> refreshTokenRepository.delete(token));
        }
    }

    /**
     * Generates a cryptographically secure random token.
     */
    private String generateSecureToken() {
        // Use UUID + random string for high entropy
        return UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
    }

    /**
     * Creates a SHA-256 hash of the token for secure storage.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Result of validating a refresh token.
     */
    public record TokenValidationResult(String userId, String tenantId) {}
}

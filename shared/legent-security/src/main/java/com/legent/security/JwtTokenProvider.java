package com.legent.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JWT token provider for generating and validating access tokens.
 * Production-ready: supports configurable secret, expiry, and claims
 * extraction.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${legent.security.jwt.secret}") String secret,
            @Value("${legent.security.jwt.expiration-ms:86400000}") long expirationMs) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException(
                "JWT secret must be at least 256 bits (32 bytes), got: " + secretBytes.length +
                ". Please set a stronger secret in LEGENT_SECURITY_JWT_SECRET environment variable.");
        }
        log.info("JWT TokenProvider initialized with secret length: {} bytes", secretBytes.length);
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a JWT token with standard claims.
     */
    public String generateToken(String userId, String tenantId, Map<String, Object> extraClaims) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        JwtBuilder builder = Jwts.builder()
                .subject(userId)
                .claim("tenantId", tenantId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey);

        if (extraClaims != null) {
            extraClaims.forEach(builder::claim);
        }

        return builder.compact();
    }

    /**
     * Generates a token with workspace/environment scope claims.
     */
    public String generateToken(
            String userId,
            String tenantId,
            String workspaceId,
            String environmentId,
            Map<String, Object> extraClaims) {
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        if (extraClaims != null) {
            claims.putAll(extraClaims);
        }
        if (workspaceId != null && !workspaceId.isBlank()) {
            claims.put("workspaceId", workspaceId);
        }
        if (environmentId != null && !environmentId.isBlank()) {
            claims.put("environmentId", environmentId);
        }
        return generateToken(userId, tenantId, claims);
    }

    /**
     * Validates a token and returns the parsed claims.
     * Enforces strict signature, expiration, and algorithm validation.
     */

    public Optional<Claims> validateToken(String token) {
        try {
            if (token == null || token.isBlank()) {
                return Optional.empty();
            }

            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (claims.getSubject() == null || claims.getSubject().isBlank()) {
                return Optional.empty();
            }
            String tenantId = claims.get("tenantId", String.class);
            if (tenantId == null || tenantId.isBlank()) {
                return Optional.empty();
            }
            Date issuedAt = claims.getIssuedAt();
            Date expiresAt = claims.getExpiration();
            if (issuedAt == null || expiresAt == null || !expiresAt.after(issuedAt)) {
                return Optional.empty();
            }

            return Optional.of(claims);
        } catch (ExpiredJwtException e) {
            log.warn("JWT token has expired");
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.error("JWT integrity check failed: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT format not supported: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Extracts user ID (subject) from a token.
     */
    public Optional<String> getUserId(String token) {
        return validateToken(token).map(Claims::getSubject);
    }

    /**
     * Extracts tenant ID from a token.
     */
    public Optional<String> getTenantId(String token) {
        return validateToken(token).map(c -> c.get("tenantId", String.class));
    }

    public Optional<String> getWorkspaceId(String token) {
        return validateToken(token).map(c -> c.get("workspaceId", String.class));
    }

    public Optional<String> getEnvironmentId(String token) {
        return validateToken(token).map(c -> c.get("environmentId", String.class));
    }

    /**
     * Extracts roles from a token.
     */
    public List<String> extractRoles(String token) {
        Optional<Claims> claimsOpt = validateToken(token);
        if (claimsOpt.isEmpty()) {
            return Collections.emptyList();
        }
        Claims claims = claimsOpt.get();
        Object roles = claims.get("roles");
        if (roles instanceof List) {
            return ((List<?>) roles).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        if (roles instanceof String) {
            return Collections.singletonList((String) roles);
        }
        return Collections.emptyList();
    }
}

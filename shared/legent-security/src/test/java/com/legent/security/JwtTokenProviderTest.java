package com.legent.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(
            SECRET,
            60000
    );

    @Test
    void generateAndValidateToken_roundTripWorks() {
        String token = tokenProvider.generateToken(
                "user-1",
                "tenant-1",
                Map.of("roles", java.util.List.of("ADMIN"))
        );

        Optional<Claims> claims = tokenProvider.validateToken(token);

        assertTrue(claims.isPresent());
        assertEquals("user-1", claims.get().getSubject());
        assertEquals("tenant-1", claims.get().get("tenantId", String.class));
    }

    @Test
    void validateToken_whenInvalid_returnsEmpty() {
        assertTrue(tokenProvider.validateToken("not-a-valid-token").isEmpty());
    }

    @Test
    void allowExpiredAccessors_returnClaimsFromExpiredSignedToken() {
        JwtTokenProvider expiredTokenProvider = new JwtTokenProvider(SECRET, -1000);
        String token = expiredTokenProvider.generateToken(
                "user-1",
                "tenant-1",
                "workspace-1",
                "prod",
                Map.of()
        );

        assertTrue(tokenProvider.validateToken(token).isEmpty());
        assertEquals("user-1", tokenProvider.getUserIdAllowExpired(token).orElseThrow());
        assertEquals("tenant-1", tokenProvider.getTenantIdAllowExpired(token).orElseThrow());
        assertEquals("workspace-1", tokenProvider.getWorkspaceIdAllowExpired(token).orElseThrow());
        assertEquals("prod", tokenProvider.getEnvironmentIdAllowExpired(token).orElseThrow());
    }
}

package com.legent.campaign.client;

import com.legent.security.JwtTokenProvider;
import com.legent.security.TenantContext;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceAuthTokenProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void tokenForMintsScopedDeliveryOperatorServiceJwt() {
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(SECRET, 86_400_000);
        ServiceAuthTokenProvider provider = new ServiceAuthTokenProvider(jwtTokenProvider, Duration.ofMinutes(2));
        TenantContext.setEnvironmentId("prod");

        String token = provider.tokenFor("tenant-1", "workspace-1");

        Claims claims = jwtTokenProvider.validateToken(token).orElseThrow();
        assertThat(claims.getSubject()).isEqualTo("service:campaign-service");
        assertThat(claims.get("tenantId", String.class)).isEqualTo("tenant-1");
        assertThat(claims.get("workspaceId", String.class)).isEqualTo("workspace-1");
        assertThat(claims.get("environmentId", String.class)).isEqualTo("prod");
        assertThat(jwtTokenProvider.extractRoles(token)).containsExactly("DELIVERY_OPERATOR");
        assertThat(claims.get("tokenType", String.class)).isEqualTo("service");
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                .isLessThanOrEqualTo(Duration.ofMinutes(2).toMillis());
    }
}

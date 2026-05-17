package com.legent.campaign.client;

import com.legent.security.JwtTokenProvider;
import com.legent.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class ServiceAuthTokenProvider {

    private static final String SERVICE_SUBJECT = "service:campaign-service";

    private final JwtTokenProvider jwtTokenProvider;
    private final Duration tokenTtl;

    public ServiceAuthTokenProvider(
            JwtTokenProvider jwtTokenProvider,
            @Value("${legent.service-auth.token-ttl:PT2M}") Duration tokenTtl) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenTtl = tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()
                ? Duration.ofMinutes(2)
                : tokenTtl;
    }

    public String tokenFor(String tenantId, String workspaceId) {
        String scopedTenantId = require(tenantId, "tenantId");
        String scopedWorkspaceId = require(workspaceId, "workspaceId");
        return jwtTokenProvider.generateToken(
                SERVICE_SUBJECT,
                scopedTenantId,
                scopedWorkspaceId,
                TenantContext.getEnvironmentId(),
                Map.of(
                        "roles", List.of("DELIVERY_OPERATOR"),
                        "tokenType", "service",
                        "service", "campaign-service"
                ),
                tokenTtl);
    }

    private String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ReadinessDependencyException(field + " is required for service authentication");
        }
        return value.trim();
    }
}

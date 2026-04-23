package com.legent.security;

import com.legent.common.constant.AppConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Servlet filter that extracts tenant ID from the X-Tenant-Id header
 * and sets it into TenantContext for the duration of the request.
 */
@Slf4j
@Component
@Order(10)
public class TenantFilter extends OncePerRequestFilter {

    /**
     * Paths that do NOT require a tenant context.
     */
    private static final Set<String> TENANT_FREE_PATHS = Set.of(
            "/api/v1/health",
            "/api/v1/health/ready",
            "/api/v1/health/live",
            "/actuator"
    );

    @Override
    protected void doFilterInternal(
            @org.springframework.lang.NonNull HttpServletRequest request,
            @org.springframework.lang.NonNull HttpServletResponse response,
            @org.springframework.lang.NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (isTenantFreePath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String tenantId = request.getHeader(AppConstants.HEADER_TENANT_ID);

        if (tenantId == null || tenantId.isBlank()) {
            log.warn("Missing X-Tenant-Id header for path: {}", path);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            
            com.legent.common.dto.ApiResponse<Void> apiResponse = com.legent.common.dto.ApiResponse.error(
                "MISSING_TENANT", 
                "X-Tenant-Id header is required", 
                "Path: " + path
            );
            
            new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValue(response.getWriter(), apiResponse);
            return;
        }

        try {
            TenantContext.setTenantId(tenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isTenantFreePath(String path) {
        return TENANT_FREE_PATHS.stream().anyMatch(path::startsWith);
    }
}

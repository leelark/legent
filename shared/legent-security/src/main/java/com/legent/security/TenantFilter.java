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

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    /**
     * Paths that do NOT require a tenant context.
     */
    private static final Set<String> TENANT_FREE_PATHS = Set.of(
            "/actuator",
            "/api/v1/health",
            "/api/v1/auth",
            "/api/v1/track/open.gif",
            "/api/v1/track/click",
            "/api/v1/tracking/o.gif",
            "/api/v1/tracking/c"
    );

    @Override
    protected void doFilterInternal(
            @org.springframework.lang.NonNull HttpServletRequest request,
            @org.springframework.lang.NonNull HttpServletResponse response,
            @org.springframework.lang.NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String path = request.getRequestURI();
            String tenantId = request.getHeader(AppConstants.HEADER_TENANT_ID);
            if (tenantId == null || tenantId.isBlank()) {
                tenantId = request.getParameter("t");
            }

            String currentTenantId = TenantContext.getTenantId();

            // If tenant is missing and path is NOT tenant-free, fail
            if ((tenantId == null || tenantId.isBlank()) && (currentTenantId == null || currentTenantId.isBlank())) {
                if (isTenantFreePath(path)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                log.warn("Missing tenant ID for path: {}", path);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json");
                com.legent.common.dto.ApiResponse<Void> apiResponse = com.legent.common.dto.ApiResponse.error(
                    "MISSING_TENANT",
                    "X-Tenant-Id header is required (or use 't' query parameter on public tracking routes)",
                    "Path: " + path
                );
                OBJECT_MAPPER.writeValue(response.getWriter(), apiResponse);
                return;
            }

            // If tenantId provided (header/param) and differs from currentTenantId (from JWT), it's a conflict
            if (tenantId != null && !tenantId.isBlank() && currentTenantId != null && !currentTenantId.isBlank() && !currentTenantId.equals(tenantId)) {
                log.error("Tenant ID conflict: JWT={}, Provided={}", currentTenantId, tenantId);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            if ((currentTenantId == null || currentTenantId.isBlank()) && tenantId != null && !tenantId.isBlank()) {
                TenantContext.setTenantId(tenantId);
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isTenantFreePath(String path) {
        return TENANT_FREE_PATHS.stream().anyMatch(path::startsWith);
    }
}

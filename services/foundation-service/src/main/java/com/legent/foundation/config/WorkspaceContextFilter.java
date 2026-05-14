package com.legent.foundation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.security.TenantContext;
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

@Slf4j
@Component
@Order(11)
public class WorkspaceContextFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Set<String> WORKSPACE_PROTECTED_PREFIXES = Set.of(
            "/api/v1/admin/configs",
            "/api/v1/admin/settings",
            "/api/v1/admin/public-content",
            "/api/v1/admin/branding",
            "/api/v1/performance-intelligence",
            "/api/v1/global",
            "/api/v1/differentiation",
            "/api/v1/compliance"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !requiresWorkspace(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String workspaceId = request.getHeader(AppConstants.HEADER_WORKSPACE_ID);
        if (workspaceId == null || workspaceId.isBlank()) {
            log.warn("Missing workspace ID for path: {}", path);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            ApiResponse<Void> apiResponse = ApiResponse.error(
                    "MISSING_WORKSPACE",
                    "X-Workspace-Id header is required",
                    "Path: " + path
            );
            OBJECT_MAPPER.writeValue(response.getWriter(), apiResponse);
            return;
        }

        String contextWorkspaceId = normalize(TenantContext.getWorkspaceId());
        String queryWorkspaceId = normalize(request.getParameter("workspaceId"));
        if (contextWorkspaceId != null && queryWorkspaceId != null && !contextWorkspaceId.equals(queryWorkspaceId)) {
            log.warn("Workspace query parameter conflicts with context for path: {}", path);
            writeError(response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "WORKSPACE_MISMATCH",
                    "workspaceId does not match the current workspace",
                    "Path: " + path);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresWorkspace(String path) {
        if (path == null) {
            return false;
        }
        for (String prefix : WORKSPACE_PROTECTED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void writeError(HttpServletResponse response,
                            int status,
                            String code,
                            String message,
                            String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        ApiResponse<Void> apiResponse = ApiResponse.error(code, message, detail);
        OBJECT_MAPPER.writeValue(response.getWriter(), apiResponse);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

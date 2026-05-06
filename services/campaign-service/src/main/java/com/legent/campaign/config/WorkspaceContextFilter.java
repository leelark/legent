package com.legent.campaign.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
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
 * Enforces workspace context for campaign APIs.
 */
@Slf4j
@Component
@Order(11)
public class WorkspaceContextFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Set<String> WORKSPACE_FREE_PATHS = Set.of(
            "/actuator",
            "/api/v1/health"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isWorkspaceFreePath(path) || !path.startsWith("/api/v1/")) {
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
                    "X-Workspace-Id header is required for campaign APIs",
                    "Path: " + path
            );
            OBJECT_MAPPER.writeValue(response.getWriter(), apiResponse);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isWorkspaceFreePath(String path) {
        return WORKSPACE_FREE_PATHS.stream().anyMatch(path::startsWith);
    }
}

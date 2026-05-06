package com.legent.automation.config;

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
 * Enforces strict workspace and request context for automation APIs.
 */
@Slf4j
@Component
@Order(11)
public class WorkspaceContextFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Set<String> CONTEXT_FREE_PATHS = Set.of(
            "/actuator",
            "/api/v1/health"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isContextFreePath(path) || !path.startsWith("/api/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String workspaceId = request.getHeader(AppConstants.HEADER_WORKSPACE_ID);
        if (workspaceId == null || workspaceId.isBlank()) {
            writeBadRequest(response, "MISSING_WORKSPACE",
                    "X-Workspace-Id header is required for automation APIs",
                    path);
            return;
        }

        String requestId = request.getHeader(AppConstants.HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            writeBadRequest(response, "MISSING_REQUEST_ID",
                    "X-Request-Id header is required for automation APIs",
                    path);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isContextFreePath(String path) {
        return CONTEXT_FREE_PATHS.stream().anyMatch(path::startsWith);
    }

    private void writeBadRequest(HttpServletResponse response,
                                 String code,
                                 String message,
                                 String path) throws IOException {
        log.warn("{} for path {}", code, path);
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        ApiResponse<Void> apiResponse = ApiResponse.error(code, message, "Path: " + path);
        OBJECT_MAPPER.writeValue(response.getWriter(), apiResponse);
    }
}

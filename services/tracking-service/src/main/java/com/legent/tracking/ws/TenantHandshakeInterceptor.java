package com.legent.tracking.ws;

import com.legent.common.constant.AppConstants;
import com.legent.security.TenantContext;
import com.legent.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

/**
 * Copies authenticated tenant/workspace context from the upgrade request into
 * the WebSocket session. Analytics sockets must not be scoped by anonymous
 * query parameters.
 */
@Component
public class TenantHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthenticated(authentication)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String tenantId = firstPresent(TenantContext.getTenantId(), principalTenant(authentication), firstHeader(request, AppConstants.HEADER_TENANT_ID));
        String workspaceId = firstPresent(TenantContext.getWorkspaceId(), principalWorkspace(authentication), firstHeader(request, AppConstants.HEADER_WORKSPACE_ID));

        if (tenantId == null || workspaceId == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        attributes.put(AppConstants.HEADER_TENANT_ID, tenantId);
        attributes.put(AppConstants.HEADER_WORKSPACE_ID, workspaceId);
        return true;
    }

    @Override
    public void afterHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @org.springframework.lang.Nullable Exception exception) {
        // No action needed
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String principalTenant(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return blankToNull(userPrincipal.getTenantId());
        }
        return null;
    }

    private String principalWorkspace(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return blankToNull(userPrincipal.getWorkspaceId());
        }
        return null;
    }

    private String firstHeader(ServerHttpRequest request, String headerName) {
        List<String> values = request.getHeaders().get(headerName);
        return values == null || values.isEmpty() ? null : blankToNull(values.get(0));
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

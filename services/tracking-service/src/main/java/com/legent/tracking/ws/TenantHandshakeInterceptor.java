package com.legent.tracking.ws;

import com.legent.common.constant.AppConstants;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

/**
 * Extracts the tenant ID from the handshake request headers and stores it in
 * the WebSocket session attributes.
 */
@Component
public class TenantHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes) {

        List<String> tenantIds = request.getHeaders().get(AppConstants.HEADER_TENANT_ID);
        String tenantId = (tenantIds != null && !tenantIds.isEmpty()) ? tenantIds.get(0) : null;
        
        if (tenantId == null) {
            String query = request.getURI().getQuery();
            if (query != null && query.contains("t=")) {
                for (String param : query.split("&")) {
                    if (param.startsWith("t=")) {
                        tenantId = param.substring(2);
                        break;
                    }
                }
            }
        }

        if (tenantId != null) {
            attributes.put(AppConstants.HEADER_TENANT_ID, tenantId);
        }

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
}

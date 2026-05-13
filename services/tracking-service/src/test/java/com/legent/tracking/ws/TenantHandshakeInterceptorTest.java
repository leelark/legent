package com.legent.tracking.ws;

import com.legent.common.constant.AppConstants;
import com.legent.security.TenantContext;
import com.legent.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantHandshakeInterceptorTest {

    private final TenantHandshakeInterceptor interceptor = new TenantHandshakeInterceptor();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void beforeHandshakeCopiesAuthenticatedTenantAndWorkspaceContext() {
        authenticate("tenant-1");
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(
                requestWithHeaders(new HttpHeaders()),
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes);

        assertThat(allowed).isTrue();
        assertThat(attributes)
                .containsEntry(AppConstants.HEADER_TENANT_ID, "tenant-1")
                .containsEntry(AppConstants.HEADER_WORKSPACE_ID, "workspace-1");
    }

    @Test
    void beforeHandshakeCopiesWorkspaceFromAuthenticatedPrincipal() {
        UserPrincipal principal = new UserPrincipal("user-1", "tenant-1", "workspace-1", "prod", Set.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(
                requestWithHeaders(new HttpHeaders()),
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes);

        assertThat(allowed).isTrue();
        assertThat(attributes)
                .containsEntry(AppConstants.HEADER_TENANT_ID, "tenant-1")
                .containsEntry(AppConstants.HEADER_WORKSPACE_ID, "workspace-1");
    }

    @Test
    void beforeHandshakeRejectsAnonymousConnection() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean allowed = interceptor.beforeHandshake(
                requestWithHeaders(new HttpHeaders()),
                response,
                mock(WebSocketHandler.class),
                new HashMap<>());

        assertThat(allowed).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void beforeHandshakeRejectsAuthenticatedConnectionWithoutWorkspaceContext() {
        authenticate("tenant-1");
        TenantContext.setTenantId("tenant-1");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean allowed = interceptor.beforeHandshake(
                requestWithHeaders(new HttpHeaders()),
                response,
                mock(WebSocketHandler.class),
                new HashMap<>());

        assertThat(allowed).isFalse();
        verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
    }

    private void authenticate(String tenantId) {
        UserPrincipal principal = new UserPrincipal("user-1", tenantId, Set.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private ServerHttpRequest requestWithHeaders(HttpHeaders headers) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getHeaders()).thenReturn(headers);
        return request;
    }
}

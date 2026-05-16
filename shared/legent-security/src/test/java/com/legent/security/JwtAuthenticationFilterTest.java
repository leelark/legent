package com.legent.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider);

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void invalidCookieOnPublicPath_allowsAnonymousRequest() throws Exception {
        when(jwtTokenProvider.validateToken(eq("bad-token"))).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/public/landing-pages/spring-sale");
        request.setCookies(new Cookie("legent_token", "bad-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(chainCalled.get());
    }

    @Test
    void scimBearerToken_allowsServiceLevelValidation() throws Exception {
        when(jwtTokenProvider.validateToken(eq("scim-token"))).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/scim/v2/Users");
        request.addHeader("Authorization", "Bearer scim-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(chainCalled.get());
    }

    @Test
    void validToken_populatesPrincipalScopeClaimsAndTenantContext() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("user-1");
        when(claims.get("tenantId", String.class)).thenReturn("tenant-1");
        when(claims.get("workspaceId", String.class)).thenReturn("workspace-1");
        when(claims.get("environmentId", String.class)).thenReturn("prod");
        when(claims.get("roles")).thenReturn(List.of("admin"));
        when(jwtTokenProvider.validateToken(eq("good-token"))).thenReturn(Optional.of(claims));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/campaigns");
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> {
            chainCalled.set(true);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

            assertEquals("user-1", principal.getUserId());
            assertEquals("tenant-1", principal.getTenantId());
            assertEquals("workspace-1", principal.getWorkspaceId());
            assertEquals("prod", principal.getEnvironmentId());
            assertEquals("tenant-1", TenantContext.getTenantId());
            assertEquals("workspace-1", TenantContext.getWorkspaceId());
            assertEquals("prod", TenantContext.getEnvironmentId());
        };

        filter.doFilter(request, response, chain);

        assertTrue(chainCalled.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void invalidCookieOnProtectedPath_returnsUnauthorized() throws Exception {
        when(jwtTokenProvider.validateToken(eq("bad-token"))).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/campaigns");
        request.setCookies(new Cookie("legent_token", "bad-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(!chainCalled.get());
    }
}

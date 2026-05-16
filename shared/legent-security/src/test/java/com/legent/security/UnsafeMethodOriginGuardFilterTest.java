package com.legent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnsafeMethodOriginGuardFilterTest {

    private final SecurityProperties properties = allowedOrigins("https://app.legent.example", "http://localhost:*");
    private final UnsafeMethodOriginGuardFilter filter = new UnsafeMethodOriginGuardFilter(Optional.of(properties));

    @Test
    void unsafeRequestFromAllowedOrigin_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/campaigns");
        request.addHeader("Origin", "https://app.legent.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(chainCalled));

        assertTrue(chainCalled.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void unsafeRequestFromWildcardAllowedOrigin_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/campaigns/1");
        request.addHeader("Origin", "http://localhost:3000");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(chainCalled));

        assertTrue(chainCalled.get());
    }

    @Test
    void unsafeRequestFromDisallowedOrigin_isRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/campaigns/1");
        request.addHeader("Origin", "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(chainCalled));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("CSRF_ORIGIN_REJECTED"));
        assertTrue(!chainCalled.get());
    }

    @Test
    void unsafeCookieRequestWithoutOriginOrReferer_isRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/campaigns");
        request.setCookies(new Cookie("legent_token", "access-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(chainCalled));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("CSRF_ORIGIN_MISSING"));
        assertTrue(!chainCalled.get());
    }

    @Test
    void unsafeNonCookieRequestWithoutOriginOrReferer_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/internal/jobs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(chainCalled));

        assertTrue(chainCalled.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void safeRequestFromDisallowedOrigin_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/campaigns");
        request.addHeader("Origin", "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(chainCalled));

        assertTrue(chainCalled.get());
    }

    private FilterChain chain(AtomicBoolean chainCalled) {
        return (request, response) -> chainCalled.set(true);
    }

    private SecurityProperties allowedOrigins(String... origins) {
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.getJwt().setSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        securityProperties.getCors().setAllowedOrigins(List.of(origins));
        return securityProperties;
    }
}

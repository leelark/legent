package com.legent.identity.controller;

import com.legent.identity.service.FederatedIdentityService;
import com.legent.identity.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SsoControllerTest {

    @Test
    void oidcCallback_setsTenantCookieHttpOnlyLikeNormalAuth() {
        FederatedIdentityService federatedIdentityService = mock(FederatedIdentityService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        SsoController controller = new SsoController(federatedIdentityService, refreshTokenService);
        ReflectionTestUtils.setField(controller, "cookieSecure", true);
        ReflectionTestUtils.setField(controller, "cookieSameSite", "Strict");

        FederatedIdentityService.FederatedLoginResult result = new FederatedIdentityService.FederatedLoginResult(
                "jwt-token",
                "user-1",
                "tenant-1",
                "workspace-1",
                List.of("ADMIN"),
                "/app"
        );
        when(federatedIdentityService.handleOidcCallback("tenant-1", "okta", "state-1", "code-1"))
                .thenReturn(result);
        when(refreshTokenService.createRefreshToken("user-1", "tenant-1", "test-agent", "10.0.0.7"))
                .thenReturn("refresh-token");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "test-agent");
        request.addHeader("X-Forwarded-For", "10.0.0.7, 10.0.0.8");
        MockHttpServletResponse response = new MockHttpServletResponse();

        var callbackResponse = controller.oidcCallback(
                "tenant-1",
                "okta",
                "state-1",
                "code-1",
                request,
                response
        );

        assertEquals(HttpStatus.FOUND, callbackResponse.getStatusCode());
        List<String> setCookieHeaders = response.getHeaders("Set-Cookie");
        assertEquals(3, setCookieHeaders.size());
        assertCookieAttributes(setCookieHeaders, "legent_token", "Path=/", "Max-Age=86400");
        assertCookieAttributes(setCookieHeaders, "legent_refresh_token", "Path=/api/v1/auth/refresh", "Max-Age=2592000");
        assertCookieAttributes(setCookieHeaders, "legent_tenant_id", "Path=/", "Max-Age=86400");
        verify(refreshTokenService).createRefreshToken("user-1", "tenant-1", "test-agent", "10.0.0.7");
    }

    @Test
    void samlAcs_setsTenantCookieHttpOnlyLikeNormalAuth() {
        FederatedIdentityService federatedIdentityService = mock(FederatedIdentityService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        SsoController controller = new SsoController(federatedIdentityService, refreshTokenService);
        ReflectionTestUtils.setField(controller, "cookieSecure", true);
        ReflectionTestUtils.setField(controller, "cookieSameSite", "Strict");

        FederatedIdentityService.FederatedLoginResult result = new FederatedIdentityService.FederatedLoginResult(
                "jwt-token",
                "user-1",
                "tenant-1",
                "workspace-1",
                List.of("ADMIN"),
                "/app"
        );
        when(federatedIdentityService.handleSamlAcs("tenant-1", "okta", "saml-response", "relay-1"))
                .thenReturn(result);
        when(refreshTokenService.createRefreshToken("user-1", "tenant-1", "test-agent", "10.0.0.7"))
                .thenReturn("refresh-token");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "test-agent");
        request.addHeader("X-Forwarded-For", "10.0.0.7, 10.0.0.8");
        MockHttpServletResponse response = new MockHttpServletResponse();

        var callbackResponse = controller.samlAcs(
                "tenant-1",
                "okta",
                "saml-response",
                "relay-1",
                request,
                response
        );

        assertEquals(HttpStatus.FOUND, callbackResponse.getStatusCode());
        List<String> setCookieHeaders = response.getHeaders("Set-Cookie");
        assertEquals(3, setCookieHeaders.size());
        assertCookieAttributes(setCookieHeaders, "legent_token", "Path=/", "Max-Age=86400");
        assertCookieAttributes(setCookieHeaders, "legent_refresh_token", "Path=/api/v1/auth/refresh", "Max-Age=2592000");
        assertCookieAttributes(setCookieHeaders, "legent_tenant_id", "Path=/", "Max-Age=86400");
        verify(refreshTokenService).createRefreshToken("user-1", "tenant-1", "test-agent", "10.0.0.7");
    }

    @Test
    void oidcCallback_whenFederationFails_doesNotSetCookies() {
        FederatedIdentityService federatedIdentityService = mock(FederatedIdentityService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        SsoController controller = new SsoController(federatedIdentityService, refreshTokenService);
        when(federatedIdentityService.handleOidcCallback("tenant-1", "okta", "state-1", "bad-code"))
                .thenThrow(new IllegalArgumentException("Invalid callback"));

        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(IllegalArgumentException.class, () -> controller.oidcCallback(
                "tenant-1",
                "okta",
                "state-1",
                "bad-code",
                new MockHttpServletRequest(),
                response
        ));
        assertTrue(response.getHeaders("Set-Cookie").isEmpty());
        verifyNoInteractions(refreshTokenService);
    }

    private void assertCookieAttributes(List<String> headers, String name, String... expectedAttributes) {
        String cookie = headers.stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing cookie " + name));
        assertTrue(cookie.contains("HttpOnly"), () -> cookie + " missing HttpOnly");
        assertTrue(cookie.contains("Secure"), () -> cookie + " missing Secure");
        assertTrue(cookie.contains("SameSite=Strict"), () -> cookie + " missing SameSite=Strict");
        for (String expectedAttribute : expectedAttributes) {
            assertTrue(cookie.contains(expectedAttribute), () -> cookie + " missing " + expectedAttribute);
        }
    }
}

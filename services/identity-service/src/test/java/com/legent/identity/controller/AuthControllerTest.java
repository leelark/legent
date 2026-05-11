package com.legent.identity.controller;

import com.legent.identity.service.AuthService;
import com.legent.identity.service.IdentityExperienceService;
import com.legent.identity.service.RefreshTokenService;
import com.legent.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

class AuthControllerTest {

    private final AuthController controller = new AuthController(
            mock(AuthService.class),
            mock(IdentityExperienceService.class),
            mock(RefreshTokenService.class),
            mock(JwtTokenProvider.class)
    );

    @BeforeEach
    void configureCookieDefaults() {
        ReflectionTestUtils.setField(controller, "cookieSecure", true);
        ReflectionTestUtils.setField(controller, "cookieSameSite", "Strict");
    }

    @Test
    void refresh_withoutRefreshCookie_returnsUnauthorizedEnvelope() {
        var response = controller.refresh(null, mock(jakarta.servlet.http.HttpServletRequest.class), mock(jakarta.servlet.http.HttpServletResponse.class));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("REFRESH_TOKEN_REQUIRED", response.getBody().getError().getErrorCode());
    }

    @Test
    void session_withoutTokenCookie_returnsUnauthorizedEnvelope() {
        var response = controller.session(null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("SESSION_NOT_FOUND", response.getBody().getError().getErrorCode());
    }

    @Test
    void login_setsSecureHttpOnlyStrictCookies() {
        AuthService authService = mock(AuthService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        AuthController authController = new AuthController(
                authService,
                mock(IdentityExperienceService.class),
                refreshTokenService,
                jwtTokenProvider
        );
        ReflectionTestUtils.setField(authController, "cookieSecure", true);
        ReflectionTestUtils.setField(authController, "cookieSameSite", "Strict");

        String token = "jwt-token";
        when(authService.login("user@example.com", "password", "tenant-1")).thenReturn(token);
        when(jwtTokenProvider.getUserId(token)).thenReturn(Optional.of("user-1"));
        when(jwtTokenProvider.extractRoles(token)).thenReturn(List.of("ADMIN"));
        when(jwtTokenProvider.getWorkspaceId(token)).thenReturn(Optional.empty());
        when(jwtTokenProvider.getEnvironmentId(token)).thenReturn(Optional.empty());
        when(refreshTokenService.createRefreshToken("user-1", "tenant-1", null, "127.0.0.1")).thenReturn("refresh-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        controllerLogin(authController, response);

        List<String> setCookieHeaders = response.getHeaders("Set-Cookie");
        assertEquals(3, setCookieHeaders.size());
        assertTrue(setCookieHeaders.stream().allMatch(cookie -> cookie.contains("HttpOnly")));
        assertTrue(setCookieHeaders.stream().allMatch(cookie -> cookie.contains("Secure")));
        assertTrue(setCookieHeaders.stream().allMatch(cookie -> cookie.contains("SameSite=Strict")));
    }

    private void controllerLogin(AuthController authController, MockHttpServletResponse response) {
        com.legent.identity.dto.LoginRequest request = new com.legent.identity.dto.LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("password");
        authController.login("tenant-1", request, new MockHttpServletRequest(), response);
    }
}

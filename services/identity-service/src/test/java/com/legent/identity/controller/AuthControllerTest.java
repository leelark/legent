package com.legent.identity.controller;

import com.legent.identity.service.AuthService;
import com.legent.identity.service.IdentityExperienceService;
import com.legent.identity.service.RefreshTokenService;
import com.legent.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class AuthControllerTest {

    private final AuthController controller = new AuthController(
            mock(AuthService.class),
            mock(IdentityExperienceService.class),
            mock(RefreshTokenService.class),
            mock(JwtTokenProvider.class)
    );

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
}

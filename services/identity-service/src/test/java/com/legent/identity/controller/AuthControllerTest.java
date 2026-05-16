package com.legent.identity.controller;

import com.legent.identity.domain.AuthInvitation;
import com.legent.identity.dto.AuthBridgeDto;
import com.legent.identity.service.AuthService;
import com.legent.identity.service.IdentityExperienceService;
import com.legent.identity.service.RefreshTokenService;
import com.legent.security.JwtTokenProvider;
import com.legent.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        var response = controller.refresh(null, null, mock(jakarta.servlet.http.HttpServletRequest.class), mock(jakarta.servlet.http.HttpServletResponse.class));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("REFRESH_TOKEN_REQUIRED", response.getBody().getError().getErrorCode());
    }

    @Test
    void refresh_preservesWorkspaceAndEnvironmentFromExistingAccessToken() {
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

        when(refreshTokenService.validateRefreshToken("refresh-token"))
                .thenReturn(Optional.of(new RefreshTokenService.TokenValidationResult("user-1", "tenant-1")));
        when(authService.getUserRoles("tenant-1", "user-1")).thenReturn(List.of("USER"));
        when(jwtTokenProvider.getUserIdAllowExpired("old-access")).thenReturn(Optional.of("user-1"));
        when(jwtTokenProvider.getTenantIdAllowExpired("old-access")).thenReturn(Optional.of("tenant-1"));
        when(jwtTokenProvider.getWorkspaceIdAllowExpired("old-access")).thenReturn(Optional.of("workspace-1"));
        when(jwtTokenProvider.getEnvironmentIdAllowExpired("old-access")).thenReturn(Optional.of("prod"));
        when(jwtTokenProvider.generateToken(eq("user-1"), eq("tenant-1"), eq("workspace-1"), eq("prod"), eq(Map.of("roles", List.of("USER")))))
                .thenReturn("new-access");
        when(refreshTokenService.createRefreshToken(eq("user-1"), eq("tenant-1"), anyString(), anyString()))
                .thenReturn("new-refresh");
        when(jwtTokenProvider.getWorkspaceId("new-access")).thenReturn(Optional.of("workspace-1"));
        when(jwtTokenProvider.getEnvironmentId("new-access")).thenReturn(Optional.of("prod"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "test-agent");
        MockHttpServletResponse response = new MockHttpServletResponse();

        var refreshResponse = authController.refresh("refresh-token", "old-access", request, response);

        assertEquals(HttpStatus.OK, refreshResponse.getStatusCode());
        assertEquals("workspace-1", refreshResponse.getBody().getData().getWorkspaceId());
        assertEquals("prod", refreshResponse.getBody().getData().getEnvironmentId());
        verify(refreshTokenService).revokeToken("refresh-token");
    }

    @Test
    void session_withoutTokenCookie_returnsUnauthorizedEnvelope() {
        var response = controller.session(null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("SESSION_NOT_FOUND", response.getBody().getError().getErrorCode());
    }

    @Test
    void invitationEndpoints_requireAuthenticatedRbac() throws Exception {
        assertPreAuthorize(
                AuthController.class.getMethod(
                        "createInvitation",
                        String.class,
                        Authentication.class,
                        AuthBridgeDto.InvitationRequest.class),
                "isAuthenticated() and @rbacEvaluator.hasPermission('user:write', principal.roles)");
        assertPreAuthorize(
                AuthController.class.getMethod(
                        "listInvitations",
                        String.class,
                        Authentication.class),
                "isAuthenticated() and @rbacEvaluator.hasPermission('user:write', principal.roles)");
    }

    @Test
    void createInvitation_whenPrincipalTenantDiffersFromHeader_rejectsBeforeServiceCall() {
        AuthService authService = mock(AuthService.class);
        AuthController authController = new AuthController(
                authService,
                mock(IdentityExperienceService.class),
                mock(RefreshTokenService.class),
                mock(JwtTokenProvider.class)
        );
        AuthBridgeDto.InvitationRequest request = new AuthBridgeDto.InvitationRequest();
        request.setEmail("invitee@example.com");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authController.createInvitation(
                        "tenant-1",
                        authentication("user-1", "tenant-2", Set.of("ADMIN")),
                        request));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(authService);
    }

    @Test
    void createInvitation_returnsTokenFreeResponse() {
        AuthService authService = mock(AuthService.class);
        AuthController authController = new AuthController(
                authService,
                mock(IdentityExperienceService.class),
                mock(RefreshTokenService.class),
                mock(JwtTokenProvider.class)
        );
        AuthBridgeDto.InvitationRequest request = new AuthBridgeDto.InvitationRequest();
        request.setEmail("invitee@example.com");
        AuthInvitation invitation = new AuthInvitation();
        invitation.setId("invitation-1");
        invitation.setTenantId("tenant-1");
        invitation.setWorkspaceId("workspace-1");
        invitation.setEmail("invitee@example.com");
        invitation.setToken("secret-token");
        invitation.setRoleKeys(List.of("VIEWER"));
        invitation.setInvitedByUserId("user-1");
        invitation.setStatus("PENDING");
        when(authService.createInvitation("tenant-1", "user-1", request)).thenReturn(invitation);

        var response = authController.createInvitation(
                "tenant-1",
                authentication("user-1", "tenant-1", Set.of("ADMIN")),
                request);

        AuthBridgeDto.InvitationResponse data = response.getData();
        assertEquals("invitation-1", data.getId());
        assertEquals("invitee@example.com", data.getEmail());
        assertEquals(List.of("VIEWER"), data.getRoleKeys());
        assertFalse(hasTokenGetter(data));
    }

    @Test
    void listInvitations_whenPrincipalTenantDiffersFromHeader_rejectsBeforeServiceCall() {
        AuthService authService = mock(AuthService.class);
        AuthController authController = new AuthController(
                authService,
                mock(IdentityExperienceService.class),
                mock(RefreshTokenService.class),
                mock(JwtTokenProvider.class)
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authController.listInvitations(
                        "tenant-1",
                        authentication("user-1", "tenant-2", Set.of("ADMIN"))));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(authService);
    }

    @Test
    void listInvitations_returnsTokenFreeResponses() {
        AuthService authService = mock(AuthService.class);
        AuthController authController = new AuthController(
                authService,
                mock(IdentityExperienceService.class),
                mock(RefreshTokenService.class),
                mock(JwtTokenProvider.class)
        );
        AuthInvitation invitation = new AuthInvitation();
        invitation.setId("invitation-1");
        invitation.setTenantId("tenant-1");
        invitation.setWorkspaceId("workspace-1");
        invitation.setEmail("invitee@example.com");
        invitation.setToken("secret-token");
        invitation.setRoleKeys(List.of("VIEWER"));
        invitation.setInvitedByUserId("user-1");
        invitation.setStatus("PENDING");
        when(authService.listInvitations("tenant-1")).thenReturn(List.of(invitation));

        var response = authController.listInvitations(
                "tenant-1",
                authentication("user-1", "tenant-1", Set.of("ADMIN")));

        AuthBridgeDto.InvitationResponse data = response.getData().getFirst();
        assertEquals("invitation-1", data.getId());
        assertEquals("invitee@example.com", data.getEmail());
        assertEquals(List.of("VIEWER"), data.getRoleKeys());
        assertFalse(hasTokenGetter(data));
    }

    @Test
    void delegationExchange_requiresAuthenticatedUserWriteRbac() throws Exception {
        assertPreAuthorize(
                AuthController.class.getMethod(
                        "exchangeDelegation",
                        String.class,
                        Authentication.class,
                        AuthBridgeDto.DelegationRequest.class,
                        jakarta.servlet.http.HttpServletResponse.class),
                "isAuthenticated() and @rbacEvaluator.hasPermission('user:write', principal.roles)");
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

    private void assertPreAuthorize(Method method, String expectedExpression) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation);
        assertEquals(expectedExpression, annotation.value());
    }

    private boolean hasTokenGetter(Object value) {
        return List.of(value.getClass().getMethods()).stream()
                .anyMatch(method -> method.getName().equals("getToken") && method.getParameterCount() == 0);
    }

    private Authentication authentication(String userId, String tenantId, Set<String> roles) {
        return new UsernamePasswordAuthenticationToken(
                new UserPrincipal(userId, tenantId, roles),
                "token");
    }
}

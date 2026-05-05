package com.legent.identity.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.identity.domain.AuthInvitation;
import com.legent.identity.dto.AuthBridgeDto;
import com.legent.identity.dto.LoginRequest;
import com.legent.identity.dto.LoginResponse;
import com.legent.identity.dto.SignupRequest;
import com.legent.identity.service.AuthService;
import com.legent.identity.service.RefreshTokenService;
import com.legent.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${legent.security.cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${legent.security.cookie.same-site:Strict}")
    private String cookieSameSite;

    private static final int COOKIE_MAX_AGE = 86400;
    private static final int REFRESH_COOKIE_MAX_AGE = 2592000;
    private static final String TOKEN_COOKIE_NAME = "legent_token";
    private static final String REFRESH_COOKIE_NAME = "legent_refresh_token";
    private static final String TENANT_COOKIE_NAME = "legent_tenant_id";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth/refresh";

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        String resolvedTenant = tenantId;
        if (resolvedTenant == null || resolvedTenant.isBlank()) {
            resolvedTenant = authService.resolveDefaultTenantId(request.getEmail())
                    .orElseThrow(() -> new IllegalArgumentException("Tenant context is required for login"));
        }

        String token = authService.login(request.getEmail(), request.getPassword(), resolvedTenant);
        String userId = jwtTokenProvider.getUserId(token).orElseThrow();

        String refreshToken = refreshTokenService.createRefreshToken(
                userId, resolvedTenant,
                httpRequest.getHeader("User-Agent"),
                getClientIp(httpRequest)
        );

        setAuthCookies(response, token, resolvedTenant, refreshToken);
        return ApiResponse.ok(new LoginResponse("success", userId, resolvedTenant, jwtTokenProvider.extractRoles(token)));
    }

    @PostMapping("/signup")
    public ApiResponse<LoginResponse> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        String token = authService.signup(request);
        String tenantId = jwtTokenProvider.getTenantId(token).orElseThrow();
        String userId = jwtTokenProvider.getUserId(token).orElseThrow();

        String refreshToken = refreshTokenService.createRefreshToken(
                userId, tenantId,
                httpRequest.getHeader("User-Agent"),
                getClientIp(httpRequest)
        );

        setAuthCookies(response, token, tenantId, refreshToken);
        return ApiResponse.ok(new LoginResponse("success", userId, tenantId, jwtTokenProvider.extractRoles(token)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeToken(refreshToken);
        }
        clearCookie(response, TOKEN_COOKIE_NAME, "/");
        clearCookie(response, REFRESH_COOKIE_NAME, REFRESH_COOKIE_PATH);
        clearCookie(response, TENANT_COOKIE_NAME, "/");
        return ApiResponse.ok(null);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ApiResponse.error("REFRESH_TOKEN_REQUIRED", "No refresh token provided", "Please provide a refresh token cookie");
        }

        var validationResult = refreshTokenService.validateRefreshToken(refreshToken);
        if (validationResult.isEmpty()) {
            return ApiResponse.error("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired", "The provided refresh token may have been revoked or expired");
        }
        var result = validationResult.get();
        refreshTokenService.revokeToken(refreshToken);

        List<String> roles = authService.getUserRoles(result.tenantId(), result.userId());
        if (roles.isEmpty()) {
            return ApiResponse.error("USER_NOT_FOUND", "User is inactive or does not exist", "Please sign in again");
        }

        String newToken = jwtTokenProvider.generateToken(result.userId(), result.tenantId(), Map.of("roles", roles));
        String newRefreshToken = refreshTokenService.createRefreshToken(
                result.userId(), result.tenantId(),
                httpRequest.getHeader("User-Agent"),
                getClientIp(httpRequest)
        );
        setAuthCookies(response, newToken, result.tenantId(), newRefreshToken);
        return ApiResponse.ok(new LoginResponse("success", result.userId(), result.tenantId(), roles));
    }

    @GetMapping("/session")
    public ApiResponse<LoginResponse> session(
            @CookieValue(name = TOKEN_COOKIE_NAME, required = false) String token) {
        if (token == null || token.isBlank()) {
            return ApiResponse.error("SESSION_NOT_FOUND", "No active session", "Please login");
        }

        var claimsOpt = jwtTokenProvider.validateToken(token);
        if (claimsOpt.isEmpty()) {
            return ApiResponse.error("INVALID_SESSION", "Session is invalid or expired", "Please login again");
        }

        var claims = claimsOpt.get();
        return ApiResponse.ok(new LoginResponse(
                "success",
                claims.getSubject(),
                claims.get("tenantId", String.class),
                jwtTokenProvider.extractRoles(token)
        ));
    }

    @GetMapping("/contexts")
    public ApiResponse<List<Map<String, Object>>> contexts(
            @CookieValue(name = TOKEN_COOKIE_NAME, required = false) String token) {
        String userId = resolveUserId(token);
        return ApiResponse.ok(authService.getAccountContexts(userId));
    }

    @PostMapping("/context/switch")
    public ApiResponse<LoginResponse> switchContext(
            @CookieValue(name = TOKEN_COOKIE_NAME, required = false) String token,
            @Valid @RequestBody AuthBridgeDto.ContextSwitchRequest request,
            HttpServletResponse response) {
        String userId = resolveUserId(token);
        String switchedToken = authService.switchContext(userId, request);
        List<String> roles = jwtTokenProvider.extractRoles(switchedToken);
        setAuthCookies(response, switchedToken, request.getTenantId(), null);
        return ApiResponse.ok(new LoginResponse("success", userId, request.getTenantId(), roles));
    }

    @PostMapping("/invitations")
    public ApiResponse<AuthInvitation> createInvitation(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @CookieValue(name = TOKEN_COOKIE_NAME, required = false) String token,
            @Valid @RequestBody AuthBridgeDto.InvitationRequest request) {
        String inviterUserId = resolveUserId(token);
        return ApiResponse.ok(authService.createInvitation(tenantId, inviterUserId, request));
    }

    @GetMapping("/invitations")
    public ApiResponse<List<AuthInvitation>> listInvitations(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ApiResponse.ok(authService.listInvitations(tenantId));
    }

    @PostMapping("/invitations/accept")
    public ApiResponse<LoginResponse> acceptInvitation(
            @Valid @RequestBody AuthBridgeDto.InvitationAcceptRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String token = authService.acceptInvitation(request);
        String tenantId = jwtTokenProvider.getTenantId(token).orElseThrow();
        String userId = jwtTokenProvider.getUserId(token).orElseThrow();
        String refreshToken = refreshTokenService.createRefreshToken(
                userId, tenantId,
                httpRequest.getHeader("User-Agent"),
                getClientIp(httpRequest)
        );
        setAuthCookies(response, token, tenantId, refreshToken);
        return ApiResponse.ok(new LoginResponse("success", userId, tenantId, jwtTokenProvider.extractRoles(token)));
    }

    @PostMapping("/delegation/exchange")
    public ApiResponse<LoginResponse> exchangeDelegation(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @CookieValue(name = TOKEN_COOKIE_NAME, required = false) String token,
            @Valid @RequestBody AuthBridgeDto.DelegationRequest request,
            HttpServletResponse response) {
        String userId = resolveUserId(token);
        String delegatedToken = authService.exchangeDelegationToken(userId, tenantId, request);
        String delegatedUserId = jwtTokenProvider.getUserId(delegatedToken).orElseThrow();
        List<String> roles = jwtTokenProvider.extractRoles(delegatedToken);
        setAuthCookies(response, delegatedToken, tenantId, null);
        return ApiResponse.ok(new LoginResponse("success", delegatedUserId, tenantId, roles));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> sessions(
            @CookieValue(name = TOKEN_COOKIE_NAME, required = false) String token) {
        String userId = resolveUserId(token);
        List<Map<String, Object>> contexts = authService.getAccountContexts(userId);
        return ApiResponse.ok(contexts);
    }

    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            var validationResult = refreshTokenService.validateRefreshToken(refreshToken);
            validationResult.ifPresent(result ->
                    refreshTokenService.revokeAllUserTokens(result.userId(), result.tenantId())
            );
        }
        clearCookie(response, TOKEN_COOKIE_NAME, "/");
        clearCookie(response, REFRESH_COOKIE_NAME, REFRESH_COOKIE_PATH);
        clearCookie(response, TENANT_COOKIE_NAME, "/");
        return ApiResponse.ok(null);
    }

    private void setAuthCookies(HttpServletResponse response, String token, String tenantId, String refreshToken) {
        ResponseCookie tokenCookie = ResponseCookie.from(TOKEN_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .maxAge(COOKIE_MAX_AGE)
                .path("/")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, tokenCookie.toString());

        if (refreshToken != null && !refreshToken.isBlank()) {
            ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .sameSite(cookieSameSite)
                    .maxAge(REFRESH_COOKIE_MAX_AGE)
                    .path(REFRESH_COOKIE_PATH)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        }

        ResponseCookie tenantCookie = ResponseCookie.from(TENANT_COOKIE_NAME, tenantId)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .maxAge(COOKIE_MAX_AGE)
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, tenantCookie.toString());
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    private void clearCookie(HttpServletResponse response, String name, String path) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .maxAge(0)
                .path(path)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("No active session");
        }
        return jwtTokenProvider.getUserId(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid session"));
    }
}

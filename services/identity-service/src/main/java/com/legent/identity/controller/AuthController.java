package com.legent.identity.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.identity.dto.SignupRequest;
import com.legent.identity.dto.LoginRequest;
import com.legent.identity.dto.LoginResponse;
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

    private static final int COOKIE_MAX_AGE = 86400; // 24 hours in seconds
    private static final int REFRESH_COOKIE_MAX_AGE = 2592000; // 30 days in seconds
    private static final String TOKEN_COOKIE_NAME = "legent_token";
    private static final String REFRESH_COOKIE_NAME = "legent_refresh_token";
    private static final String TENANT_COOKIE_NAME = "legent_tenant_id";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth/refresh";

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        String token = authService.login(request.getEmail(), request.getPassword(), tenantId);

        // Extract userId from token to create refresh token
        String userId = jwtTokenProvider.getUserId(token).orElseThrow();

        // Create and set refresh token (Fix 33)
        String refreshToken = refreshTokenService.createRefreshToken(
                userId, tenantId,
                httpRequest.getHeader("User-Agent"),
                getClientIp(httpRequest)
        );

        // Set HTTP-only, Secure, SameSite=Strict cookies
        setAuthCookies(response, token, tenantId, refreshToken);

        // Get roles for response (userId already extracted)
        List<String> roles = jwtTokenProvider.extractRoles(token);

        // Return user info (token is in HTTP-only cookie)
        return ApiResponse.ok(new LoginResponse("success", userId, tenantId, roles));
    }

    @PostMapping("/signup")
    public ApiResponse<LoginResponse> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        // AuthService.signup generates a new tenant and returns token with embedded tenantId
        String token = authService.signup(request);

        // Parse tenantId and userId from token
        String tenantId = jwtTokenProvider.getTenantId(token).orElseThrow();
        String userId = jwtTokenProvider.getUserId(token).orElseThrow();

        // Create and set refresh token (Fix 33)
        String refreshToken = refreshTokenService.createRefreshToken(
                userId, tenantId,
                httpRequest.getHeader("User-Agent"),
                getClientIp(httpRequest)
        );

        // Set HTTP-only, Secure, SameSite=Strict cookies
        setAuthCookies(response, token, tenantId, refreshToken);

        // Get roles for response (userId and tenantId already extracted)
        List<String> roles = jwtTokenProvider.extractRoles(token);

        // Return user info (token is in HTTP-only cookie)
        return ApiResponse.ok(new LoginResponse("success", userId, tenantId, roles));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        // Revoke the refresh token if present
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeToken(refreshToken);
        }

        // Clear auth cookies - use correct paths
        clearCookie(response, TOKEN_COOKIE_NAME, "/");
        clearCookie(response, REFRESH_COOKIE_NAME, REFRESH_COOKIE_PATH);
        clearCookie(response, TENANT_COOKIE_NAME, "/");
        return ApiResponse.ok(null);
    }

    /**
     * Refresh endpoint (Fix 33): Exchanges a valid refresh token for a new access token.
     * Allows users to extend their session without re-entering credentials.
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        if (refreshToken == null || refreshToken.isBlank()) {
            return ApiResponse.error("REFRESH_TOKEN_REQUIRED", "No refresh token provided", "Please provide a refresh token cookie");
        }

        // Validate refresh token and get user info
        var validationResult = refreshTokenService.validateRefreshToken(refreshToken);
        if (validationResult.isEmpty()) {
            return ApiResponse.error("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired", "The provided refresh token may have been revoked or expired");
        }

        var result = validationResult.get();

        // Revoke the old refresh token (rotation for security)
        refreshTokenService.revokeToken(refreshToken);

        List<String> roles = authService.getUserRoles(result.tenantId(), result.userId());
        if (roles.isEmpty()) {
            return ApiResponse.error("USER_NOT_FOUND", "User is inactive or does not exist", "Please sign in again");
        }

        // Generate new access token
        String newToken = jwtTokenProvider.generateToken(
                result.userId(),
                result.tenantId(),
                Map.of("roles", roles)
        );

        // Create new refresh token
        String newRefreshToken = refreshTokenService.createRefreshToken(
                result.userId(), result.tenantId(),
                httpRequest.getHeader("User-Agent"),
                getClientIp(httpRequest)
        );

        // Set new cookies
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
        String userId = claims.getSubject();
        String tenantId = claims.get("tenantId", String.class);
        List<String> roles = jwtTokenProvider.extractRoles(token);

        return ApiResponse.ok(new LoginResponse("success", userId, tenantId, roles));
    }

    /**
     * Logout from all devices: Revokes all refresh tokens for the user.
     */
    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {

        if (refreshToken != null && !refreshToken.isBlank()) {
            // Validate to get user info
            var validationResult = refreshTokenService.validateRefreshToken(refreshToken);
            validationResult.ifPresent(result ->
                    refreshTokenService.revokeAllUserTokens(result.userId(), result.tenantId())
            );
        }

        // Clear cookies - use correct paths to match original cookie paths
        clearCookie(response, TOKEN_COOKIE_NAME, "/");
        clearCookie(response, REFRESH_COOKIE_NAME, REFRESH_COOKIE_PATH);
        clearCookie(response, TENANT_COOKIE_NAME, "/");
        return ApiResponse.ok(null);
    }

    /**
     * Sets HTTP-only, Secure, SameSite=Strict cookies for authentication.
     * These cookies are immune to XSS attacks and cannot be accessed by JavaScript.
     */
    private void setAuthCookies(HttpServletResponse response, String token, String tenantId, String refreshToken) {
        // JWT access token cookie - HTTP-only, SameSite=Strict
        // secure flag is environment-based (false for local HTTP, true for production HTTPS)
        ResponseCookie tokenCookie = ResponseCookie.from(TOKEN_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .maxAge(COOKIE_MAX_AGE)
                .path("/")
                .build();

        // Refresh token cookie - longer-lived, used to obtain new access tokens
        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .maxAge(REFRESH_COOKIE_MAX_AGE)  // 30 days
                .path(REFRESH_COOKIE_PATH)  // Only sent to refresh endpoint
                .build();

        // Tenant ID cookie - HTTP-only (not sensitive but prevents tampering)
        ResponseCookie tenantCookie = ResponseCookie.from(TENANT_COOKIE_NAME, tenantId)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .maxAge(COOKIE_MAX_AGE)
                .path("/")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, tokenCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, tenantCookie.toString());
    }

    /**
     * Extracts client IP address from request.
     */
    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    /**
     * Clears a cookie by setting max-age to 0.
     * Must use the same path as when the cookie was created.
     */
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
}

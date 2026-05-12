package com.legent.identity.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.identity.dto.FederationDto;
import com.legent.identity.service.FederatedIdentityService;
import com.legent.identity.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sso")
@RequiredArgsConstructor
public class SsoController {

    private static final int COOKIE_MAX_AGE = 86400;
    private static final int REFRESH_COOKIE_MAX_AGE = 2592000;
    private static final String TOKEN_COOKIE_NAME = "legent_token";
    private static final String REFRESH_COOKIE_NAME = "legent_refresh_token";
    private static final String TENANT_COOKIE_NAME = "legent_tenant_id";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth/refresh";

    private final FederatedIdentityService federatedIdentityService;
    private final RefreshTokenService refreshTokenService;

    @Value("${legent.security.cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${legent.security.cookie.same-site:Strict}")
    private String cookieSameSite;

    @GetMapping("/{tenantId}/{providerKey}/login")
    public ResponseEntity<Void> login(
            @PathVariable String tenantId,
            @PathVariable String providerKey,
            @RequestParam(required = false) String redirectAfter) {
        Map<String, Object> start = federatedIdentityService.startLogin(tenantId, providerKey, redirectAfter);
        return ResponseEntity.status(302).location(URI.create(String.valueOf(start.get("location")))).build();
    }

    @GetMapping("/{tenantId}/{providerKey}/oidc/callback")
    public ResponseEntity<Void> oidcCallback(
            @PathVariable String tenantId,
            @PathVariable String providerKey,
            @RequestParam String state,
            @RequestParam String code,
            HttpServletRequest request,
            HttpServletResponse response) {
        FederatedIdentityService.FederatedLoginResult result = federatedIdentityService.handleOidcCallback(tenantId, providerKey, state, code);
        setAuthCookies(response, result, request);
        return ResponseEntity.status(302).location(URI.create(result.redirectAfter())).build();
    }

    @PostMapping("/{tenantId}/{providerKey}/saml/acs")
    public ResponseEntity<Void> samlAcs(
            @PathVariable String tenantId,
            @PathVariable String providerKey,
            @RequestParam("SAMLResponse") String samlResponse,
            @RequestParam(value = "RelayState", required = false) String relayState,
            HttpServletRequest request,
            HttpServletResponse response) {
        FederatedIdentityService.FederatedLoginResult result = federatedIdentityService.handleSamlAcs(tenantId, providerKey, samlResponse, relayState);
        setAuthCookies(response, result, request);
        return ResponseEntity.status(302).location(URI.create(result.redirectAfter())).build();
    }

    @PostMapping("/{tenantId}/{providerKey}/saml/acs-json")
    public ApiResponse<Map<String, Object>> samlAcsJson(
            @PathVariable String tenantId,
            @PathVariable String providerKey,
            @Valid @RequestBody FederationDto.SamlAcsRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        FederatedIdentityService.FederatedLoginResult result = federatedIdentityService.handleSamlAcs(
                tenantId, providerKey, body.getSamlResponse(), body.getRelayState());
        setAuthCookies(response, result, request);
        return ApiResponse.ok(Map.of(
                "status", "success",
                "userId", result.userId(),
                "tenantId", result.tenantId(),
                "workspaceId", result.workspaceId(),
                "roles", result.roles(),
                "redirectAfter", result.redirectAfter()
        ));
    }

    @GetMapping(value = "/{tenantId}/{providerKey}/metadata", produces = MediaType.APPLICATION_XML_VALUE)
    public String samlMetadata(
            @PathVariable String tenantId,
            @PathVariable String providerKey,
            HttpServletRequest request) {
        String acs = UriComponentsBuilder.fromHttpUrl(request.getRequestURL().toString())
                .replacePath("/api/v1/sso/" + tenantId + "/" + providerKey + "/saml/acs")
                .replaceQuery(null)
                .toUriString();
        return federatedIdentityService.samlMetadata(tenantId, providerKey, acs);
    }

    private void setAuthCookies(HttpServletResponse response, FederatedIdentityService.FederatedLoginResult result, HttpServletRequest request) {
        String refreshToken = refreshTokenService.createRefreshToken(
                result.userId(),
                result.tenantId(),
                request.getHeader("User-Agent"),
                getClientIp(request)
        );
        ResponseCookie tokenCookie = ResponseCookie.from(TOKEN_COOKIE_NAME, result.token())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .maxAge(COOKIE_MAX_AGE)
                .path("/")
                .build();
        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .maxAge(REFRESH_COOKIE_MAX_AGE)
                .path(REFRESH_COOKIE_PATH)
                .build();
        ResponseCookie tenantCookie = ResponseCookie.from(TENANT_COOKIE_NAME, result.tenantId())
                .httpOnly(false)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .maxAge(COOKIE_MAX_AGE)
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, tokenCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, tenantCookie.toString());
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

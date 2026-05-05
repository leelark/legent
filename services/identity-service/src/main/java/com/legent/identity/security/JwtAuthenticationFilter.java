package com.legent.identity.security;

import com.legent.common.constant.AppConstants;
import com.legent.security.JwtTokenProvider;
import com.legent.security.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Filter that validates JWT bearer tokens and populates Spring Security context.
 */
@Slf4j
@Component("identityJwtAuthenticationFilter")
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    private static final String TOKEN_COOKIE_NAME = "legent_token";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token == null || token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<Claims> claimsOptional = jwtTokenProvider.validateToken(token);
        if (claimsOptional.isEmpty()) {
            unauthorized(response, "INVALID_TOKEN", "JWT token is invalid or expired");
            return;
        }

        Claims claims = claimsOptional.get();
        String userId = claims.getSubject();
        String tenantId = claims.get("tenantId", String.class);
        String workspaceId = claims.get("workspaceId", String.class);
        String environmentId = claims.get("environmentId", String.class);
        Set<String> roles = extractRoles(claims);

        if (tenantId != null && !tenantId.isBlank() && TenantContext.getTenantId() == null) {
            TenantContext.setTenantId(tenantId);
        }
        if (userId != null && !userId.isBlank()) {
            TenantContext.setUserId(userId);
        }
        if (workspaceId != null && !workspaceId.isBlank()) {
            TenantContext.setWorkspaceId(workspaceId);
        }
        if (environmentId != null && !environmentId.isBlank()) {
            TenantContext.setEnvironmentId(environmentId);
        }

        UserPrincipal principal = new UserPrincipal(userId, tenantId, roles);
        Collection<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toSet());

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                token,
                authorities
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private Set<String> extractRoles(Claims claims) {
        Object rawRoles = claims.get("roles");
        if (rawRoles instanceof List<?>) {
            return ((List<?>) rawRoles).stream()
                    .filter(value -> value != null)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
        }

        if (rawRoles instanceof String) {
            String value = ((String) rawRoles).trim();
            return value.isEmpty()
                    ? Collections.emptySet()
                    : Collections.singleton(value.toUpperCase());
        }

        return Collections.emptySet();
    }

    private void unauthorized(HttpServletResponse response, String errorCode, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"success\":false,\"error\":{\"errorCode\":\"%s\",\"message\":\"%s\"}}",
                errorCode,
                message
        ));
    }

    /**
     * Extracts JWT token from HTTP-only cookie or Authorization header.
     * Priority: 1) Cookie (secure, preferred) 2) Header (backward compatibility)
     */
    private String extractToken(HttpServletRequest request) {
        // First check HTTP-only cookie (secure method)
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if (TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        // Fallback to Authorization header (backward compatibility)
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader != null && authorizationHeader.startsWith(AppConstants.BEARER_PREFIX)) {
            return authorizationHeader.substring(AppConstants.BEARER_PREFIX.length()).trim();
        }

        return null;
    }
}

package com.legent.security;

import com.legent.common.constant.AppConstants;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
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
 * Filter that validates JWT tokens from Bearer header or HTTP-only cookies.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(5)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private static final String TOKEN_COOKIE_NAME = "legent_token";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (!StringUtils.hasText(token)) {
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

        // Populate TenantContext if available
        if (tenantId != null && !tenantId.isBlank()) {
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
                null,
                authorities
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts JWT token from Authorization header or HTTP-only cookie.
     */
    private String extractToken(HttpServletRequest request) {
        // First, try to get from Authorization header (Bearer token)
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader != null && authorizationHeader.startsWith(AppConstants.BEARER_PREFIX)) {
            String token = authorizationHeader.substring(AppConstants.BEARER_PREFIX.length()).trim();
            if (StringUtils.hasText(token)) {
                return token;
            }
        }

        // Fallback to cookie-based auth
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    String token = cookie.getValue();
                    if (StringUtils.hasText(token)) {
                        return token;
                    }
                }
            }
        }

        return null;
    }

    private Set<String> extractRoles(Claims claims) {
        Object rawRoles = claims.get("roles");
        if (rawRoles instanceof List<?>) {
            return ((List<?>) rawRoles).stream()
                    .filter(java.util.Objects::nonNull)
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
}

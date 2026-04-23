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
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader == null || !authorizationHeader.startsWith(AppConstants.BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(AppConstants.BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            unauthorized(response, "INVALID_TOKEN", "Authorization header contains no Bearer token");
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
        Set<String> roles = extractRoles(claims);

        if (tenantId != null && !tenantId.isBlank() && TenantContext.getTenantId() == null) {
            TenantContext.setTenantId(tenantId);
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
}

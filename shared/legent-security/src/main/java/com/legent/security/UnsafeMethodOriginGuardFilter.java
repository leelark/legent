package com.legent.security;

import com.legent.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Blocks cross-site browser writes when auth cookies are present.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class UnsafeMethodOriginGuardFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Set<String> AUTH_COOKIE_NAMES = Set.of("legent_token", "legent_refresh_token");

    private final Optional<SecurityProperties> securityProperties;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (isSafeMethod(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        String candidateOrigin = hasText(origin) ? origin : originFromReferer(referer);

        if (securityProperties.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!hasText(candidateOrigin)) {
            if (hasAuthCookie(request)) {
                log.warn("Blocked unsafe cookie-authenticated request without Origin/Referer to {}", request.getRequestURI());
                reject(response, "CSRF_ORIGIN_MISSING", "Origin or Referer is required for unsafe requests");
                return;
            }

            filterChain.doFilter(request, response);
            return;
        }

        if (isAllowedOrigin(securityProperties.get(), candidateOrigin)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Blocked unsafe request from disallowed origin {} to {}", candidateOrigin, request.getRequestURI());
        reject(response, "CSRF_ORIGIN_REJECTED", "Origin is not in the configured CORS allow-list");
    }

    private void reject(HttpServletResponse response, String code, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        OBJECT_MAPPER.writeValue(response.getWriter(), ApiResponse.error(
                code,
                "Unsafe cross-site request rejected",
                detail
        ));
    }

    private boolean isSafeMethod(String method) {
        return "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
    }

    private boolean isAllowedOrigin(SecurityProperties securityProperties, String origin) {
        List<String> allowedOrigins = securityProperties.getCors().getAllowedOrigins();
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            return false;
        }

        String normalizedOrigin = origin.trim();
        return allowedOrigins.stream()
                .filter(this::hasText)
                .map(String::trim)
                .anyMatch(allowed -> matchesOriginPattern(allowed, normalizedOrigin));
    }

    private boolean matchesOriginPattern(String allowed, String origin) {
        if ("*".equals(allowed)) {
            return true;
        }
        if (allowed.equalsIgnoreCase(origin)) {
            return true;
        }
        if (!allowed.contains("*")) {
            return false;
        }

        StringBuilder regex = new StringBuilder("^");
        for (char character : allowed.toCharArray()) {
            if (character == '*') {
                regex.append(".*");
            } else {
                regex.append("\\Q").append(character).append("\\E");
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE).matcher(origin).matches();
    }

    private String originFromReferer(String referer) {
        if (!hasText(referer)) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!hasText(scheme) || !hasText(host)) {
                return null;
            }
            int port = uri.getPort();
            String defaultPort = ("https".equalsIgnoreCase(scheme) && port == 443)
                    || ("http".equalsIgnoreCase(scheme) && port == 80)
                    || port < 0
                    ? ""
                    : ":" + port;
            return scheme.toLowerCase(Locale.ROOT) + "://" + host.toLowerCase(Locale.ROOT) + defaultPort;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean hasAuthCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (cookie != null && AUTH_COOKIE_NAMES.contains(cookie.getName()) && hasText(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

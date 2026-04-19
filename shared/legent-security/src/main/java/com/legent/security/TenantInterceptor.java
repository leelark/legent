package com.legent.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC interceptor that injects tenant_id into JPA entities
 * via the TenantContext. Works alongside TenantFilter.
 * <p>
 * Can be extended with context-awareness for user ID, roles, etc.
 */
@Slf4j
@Component
@SuppressWarnings("null")
public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            @org.springframework.lang.NonNull HttpServletRequest request,
            @org.springframework.lang.NonNull HttpServletResponse response,
            @org.springframework.lang.NonNull Object handler) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            log.debug("Processing request for tenant: {}", tenantId);
        }
        return true;
    }

    @Override
    public void afterCompletion(
            @org.springframework.lang.NonNull HttpServletRequest request,
            @org.springframework.lang.NonNull HttpServletResponse response,
            Object handler,
            @org.springframework.lang.Nullable Exception ex) {
        // TenantContext is already cleared by TenantFilter
        // This hook is available for future extension (metrics, audit)
    }
}

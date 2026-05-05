package com.legent.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Thread-local holder for the current tenant context.
 * Set by TenantFilter on every request; cleared after request completes.
 * <p>
 * This is the single source of truth for "which tenant is this request for?"
 * Used by all tenant-aware repositories and services.
 */
@Slf4j
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_WORKSPACE = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ENVIRONMENT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_REQUEST = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_CORRELATION = new ThreadLocal<>();

    private TenantContext() {
        // Utility class
    }

    public static void setTenantId(String tenantId) {
        log.trace("Setting tenant context: {}", tenantId);
        CURRENT_TENANT.set(tenantId);
    }

    @Nullable
    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    @NonNull
    public static String requireTenantId() {
        String tenantId = CURRENT_TENANT.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Tenant context is not set");
        }
        return tenantId;
    }

    public static void setUserId(String userId) {
        CURRENT_USER.set(userId);
    }

    @Nullable
    public static String getUserId() {
        return CURRENT_USER.get();
    }

    public static void setWorkspaceId(String workspaceId) {
        CURRENT_WORKSPACE.set(workspaceId);
    }

    @Nullable
    public static String getWorkspaceId() {
        return CURRENT_WORKSPACE.get();
    }

    @NonNull
    public static String requireWorkspaceId() {
        String workspaceId = CURRENT_WORKSPACE.get();
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalStateException("Workspace context is not set");
        }
        return workspaceId;
    }

    public static void setEnvironmentId(String environmentId) {
        CURRENT_ENVIRONMENT.set(environmentId);
    }

    @Nullable
    public static String getEnvironmentId() {
        return CURRENT_ENVIRONMENT.get();
    }

    public static void setRequestId(String requestId) {
        CURRENT_REQUEST.set(requestId);
    }

    @Nullable
    public static String getRequestId() {
        return CURRENT_REQUEST.get();
    }

    public static void setCorrelationId(String correlationId) {
        CURRENT_CORRELATION.set(correlationId);
    }

    @Nullable
    public static String getCorrelationId() {
        return CURRENT_CORRELATION.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_USER.remove();
        CURRENT_WORKSPACE.remove();
        CURRENT_ENVIRONMENT.remove();
        CURRENT_REQUEST.remove();
        CURRENT_CORRELATION.remove();
    }
}

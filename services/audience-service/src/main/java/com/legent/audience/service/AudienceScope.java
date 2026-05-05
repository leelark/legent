package com.legent.audience.service;

import com.legent.security.TenantContext;

/**
 * Central audience scope helpers.
 */
public final class AudienceScope {

    private AudienceScope() {
    }

    public static String tenantId() {
        return TenantContext.requireTenantId();
    }

    public static String workspaceId() {
        return TenantContext.requireWorkspaceId();
    }
}

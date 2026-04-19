package com.legent.test;

import com.legent.security.TenantContext;

/**
 * Test utility for setting up tenant context in unit tests.
 */
public final class MockTenantContext {

    public static final String DEFAULT_TENANT_ID = "test-tenant-001";
    public static final String DEFAULT_USER_ID = "test-user-001";

    private MockTenantContext() {
        // Utility class
    }

    /**
     * Sets up a default tenant context for testing.
     */
    public static void setup() {
        TenantContext.setTenantId(DEFAULT_TENANT_ID);
        TenantContext.setUserId(DEFAULT_USER_ID);
    }

    /**
     * Sets up a specific tenant context.
     */
    public static void setup(String tenantId, String userId) {
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(userId);
    }

    /**
     * Clears the tenant context.
     */
    public static void clear() {
        TenantContext.clear();
    }
}

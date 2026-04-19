package com.legent.cache.service;

import com.legent.security.TenantContext;

/**
 * Utility for generating tenant-scoped cache keys.
 * Ensures cache isolation between tenants.
 */
public final class TenantCacheKeyGenerator {

    private TenantCacheKeyGenerator() {
        // Utility class
    }

    /**
     * Generates a tenant-scoped cache key.
     * Format: {prefix}:{tenantId}:{key}
     */
    public static String key(String prefix, String key) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return prefix + "global:" + key;
        }
        return prefix + tenantId + ":" + key;
    }

    /**
     * Generates a global (non-tenant) cache key.
     * Format: {prefix}:global:{key}
     */
    public static String globalKey(String prefix, String key) {
        return prefix + "global:" + key;
    }

    /**
     * Generates a pattern for deleting all tenant keys under a prefix.
     * Format: {prefix}:{tenantId}:*
     */
    public static String tenantPattern(String prefix) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return prefix + "*";
        }
        return prefix + tenantId + ":*";
    }
}

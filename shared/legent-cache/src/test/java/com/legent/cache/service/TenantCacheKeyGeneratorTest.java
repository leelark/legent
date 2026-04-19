package com.legent.cache.service;

import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantCacheKeyGeneratorTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void key_whenTenantPresent_returnsTenantScopedKey() {
        TenantContext.setTenantId("tenant-42");

        assertEquals("segment:tenant-42:active", TenantCacheKeyGenerator.key("segment:", "active"));
    }

    @Test
    void key_whenTenantMissing_returnsGlobalKey() {
        assertEquals("segment:global:active", TenantCacheKeyGenerator.key("segment:", "active"));
    }
}

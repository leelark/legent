package com.legent.platform.config;

import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AsyncConfigTest {

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void taskDecoratorPropagatesWorkspaceEnvironmentAndUserContextThenClearsThread() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setEnvironmentId("environment-1");
        TenantContext.setUserId("user-1");

        Runnable decorated = new AsyncConfig.TenantContextTaskDecorator().decorate(() -> {
            assertEquals("tenant-1", TenantContext.getTenantId());
            assertEquals("workspace-1", TenantContext.getWorkspaceId());
            assertEquals("environment-1", TenantContext.getEnvironmentId());
            assertEquals("user-1", TenantContext.getUserId());
        });

        TenantContext.clear();
        decorated.run();

        assertNull(TenantContext.getTenantId());
        assertNull(TenantContext.getWorkspaceId());
        assertNull(TenantContext.getEnvironmentId());
        assertNull(TenantContext.getUserId());
    }
}

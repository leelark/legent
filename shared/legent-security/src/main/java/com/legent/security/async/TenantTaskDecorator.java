package com.legent.security.async;

import com.legent.security.TenantContext;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

/**
 * Propagates tenant and user context from the parent thread to the async task thread.
 */
public class TenantTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        return () -> {
            try {
                TenantContext.setTenantId(tenantId);
                TenantContext.setUserId(userId);
                runnable.run();
            } finally {
                TenantContext.clear();
            }
        };
    }
}

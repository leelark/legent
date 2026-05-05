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
        String workspaceId = TenantContext.getWorkspaceId();
        String environmentId = TenantContext.getEnvironmentId();
        String requestId = TenantContext.getRequestId();
        String correlationId = TenantContext.getCorrelationId();

        return () -> {
            try {
                TenantContext.setTenantId(tenantId);
                TenantContext.setUserId(userId);
                TenantContext.setWorkspaceId(workspaceId);
                TenantContext.setEnvironmentId(environmentId);
                TenantContext.setRequestId(requestId);
                TenantContext.setCorrelationId(correlationId);
                runnable.run();
            } finally {
                TenantContext.clear();
            }
        };
    }
}

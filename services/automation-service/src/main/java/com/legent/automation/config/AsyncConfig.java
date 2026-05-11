package com.legent.automation.config;

import com.legent.security.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration("automationAsyncConfig")
@Slf4j
public class AsyncConfig {

    @Bean(name = "workflowExecutor")
    public Executor workflowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("workflow-");
        executor.setTaskDecorator(new TenantContextTaskDecorator());
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * TaskDecorator that propagates TenantContext to async threads and ensures cleanup.
     * Prevents cross-tenant data leaks in thread pool reuse scenarios.
     */
    public static class TenantContextTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            String tenantId = TenantContext.getTenantId();
            String userId = TenantContext.getUserId();
            String workspaceId = TenantContext.getWorkspaceId();
            String environmentId = TenantContext.getEnvironmentId();
            String requestId = TenantContext.getRequestId();
            String correlationId = TenantContext.getCorrelationId();
            return () -> {
                try {
                    if (tenantId != null) {
                        TenantContext.setTenantId(tenantId);
                    }
                    if (userId != null) {
                        TenantContext.setUserId(userId);
                    }
                    if (workspaceId != null) {
                        TenantContext.setWorkspaceId(workspaceId);
                    }
                    if (environmentId != null) {
                        TenantContext.setEnvironmentId(environmentId);
                    }
                    if (requestId != null) {
                        TenantContext.setRequestId(requestId);
                    }
                    if (correlationId != null) {
                        TenantContext.setCorrelationId(correlationId);
                    }
                    runnable.run();
                } finally {
                    TenantContext.clear();
                }
            };
        }
    }
}

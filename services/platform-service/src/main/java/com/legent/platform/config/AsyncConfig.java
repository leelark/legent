package com.legent.platform.config;

import com.legent.security.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration("platformAsyncConfig")
@Slf4j
public class AsyncConfig {

    @Bean(name = "webhookExecutor")
    public Executor webhookExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("wh-disp-");
        executor.setTaskDecorator(new TenantContextTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * LEGENT-CRIT-005: Dedicated executor for webhook retry processing with parallelism.
     * Supports up to 10 concurrent retries to prevent queue buildup.
     */
    @Bean(name = "webhookRetryExecutor")
    public Executor webhookRetryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("wh-retry-");
        executor.setTaskDecorator(new TenantContextTaskDecorator());
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
            return () -> {
                try {
                    if (tenantId != null) {
                        TenantContext.setTenantId(tenantId);
                    }
                    if (userId != null) {
                        TenantContext.setUserId(userId);
                    }
                    runnable.run();
                } finally {
                    TenantContext.clear();
                }
            };
        }
    }
}

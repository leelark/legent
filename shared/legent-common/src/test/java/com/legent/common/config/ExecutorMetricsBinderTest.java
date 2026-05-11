package com.legent.common.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExecutorMetricsBinderTest {

    @Test
    void afterSingletonsInstantiated_bindsThreadPoolExecutorMetrics() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(MeterRegistry.class, SimpleMeterRegistry::new);
        context.registerBean("testExecutor", ThreadPoolTaskExecutor.class, () -> {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(2);
            executor.setQueueCapacity(4);
            executor.setThreadNamePrefix("test-");
            return executor;
        });
        context.registerBean(ExecutorMetricsBinder.class);

        try {
            context.refresh();
            MeterRegistry registry = context.getBean(MeterRegistry.class);

            assertNotNull(registry.find("legent.executor.active").tag("executor", "testExecutor").gauge());
            assertNotNull(registry.find("legent.executor.queue.size").tag("executor", "testExecutor").gauge());
            assertNotNull(registry.find("legent.executor.saturation.ratio").tag("executor", "testExecutor").gauge());
        } finally {
            context.close();
        }
    }
}

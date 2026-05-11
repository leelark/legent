package com.legent.common.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Publishes saturation gauges for every bounded ThreadPoolTaskExecutor bean.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorMetricsBinder implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final Set<String> boundExecutors = ConcurrentHashMap.newKeySet();

    @Override
    public void afterSingletonsInstantiated() {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }

        applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class)
                .forEach((name, executor) -> bindExecutor(registry, name, executor));
    }

    private void bindExecutor(MeterRegistry registry, String name, ThreadPoolTaskExecutor executor) {
        if (!boundExecutors.add(name)) {
            return;
        }

        Gauge.builder("legent.executor.active", executor, ThreadPoolTaskExecutor::getActiveCount)
                .description("Active executor threads")
                .tag("executor", name)
                .register(registry);
        Gauge.builder("legent.executor.pool.size", executor, ThreadPoolTaskExecutor::getPoolSize)
                .description("Current executor pool size")
                .tag("executor", name)
                .register(registry);
        Gauge.builder("legent.executor.queue.size", executor, this::queueSize)
                .description("Executor queue size")
                .tag("executor", name)
                .register(registry);
        Gauge.builder("legent.executor.queue.remaining", executor, this::queueRemainingCapacity)
                .description("Executor queue remaining capacity")
                .tag("executor", name)
                .register(registry);
        Gauge.builder("legent.executor.saturation.ratio", executor, this::saturationRatio)
                .description("Executor saturation ratio from 0.0 to 1.0")
                .tag("executor", name)
                .register(registry);

        log.info("Bound executor saturation metrics for {}", name);
    }

    private double queueSize(ThreadPoolTaskExecutor executor) {
        return threadPool(executor).getQueue().size();
    }

    private double queueRemainingCapacity(ThreadPoolTaskExecutor executor) {
        return threadPool(executor).getQueue().remainingCapacity();
    }

    private double saturationRatio(ThreadPoolTaskExecutor executor) {
        ThreadPoolExecutor threadPool = threadPool(executor);
        int maxPoolSize = Math.max(threadPool.getMaximumPoolSize(), 1);
        int queueSize = threadPool.getQueue().size();
        int queueCapacity = Math.max(queueSize + threadPool.getQueue().remainingCapacity(), 1);
        double threadSaturation = (double) threadPool.getActiveCount() / maxPoolSize;
        double queueSaturation = (double) queueSize / queueCapacity;
        return Math.min(1.0d, Math.max(threadSaturation, queueSaturation));
    }

    private ThreadPoolExecutor threadPool(ThreadPoolTaskExecutor executor) {
        return executor.getThreadPoolExecutor();
    }
}

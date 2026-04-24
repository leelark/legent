package com.legent.deliverability.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration("deliverabilityAsyncConfig")
public class AsyncConfig {

    @Bean(name = "deliverabilityExecutor")
    public Executor deliverabilityExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("deliv-");
        executor.initialize();
        return executor;
    }
}

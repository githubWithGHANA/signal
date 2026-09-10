package com.quantlab.signal.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "positionDataProcessingExecutor")
    public Executor dataProcessingExecutor  () {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); // Minimum threads
        executor.setMaxPoolSize(4); // Maximum threads
        executor.setQueueCapacity(100); // Queue for pending tasks
        executor.setThreadNamePrefix("RedisProcessor-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "bodMailExecutor")
    public Executor bodMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("BodMail-");
        executor.initialize();
        return executor;
    }
}

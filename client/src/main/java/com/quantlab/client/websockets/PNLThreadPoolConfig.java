package com.quantlab.client.websockets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class PNLThreadPoolConfig {


    @Bean("finalPNL")
    public Executor singleTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);          // only 1 thread
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);         // just one waiting task
        executor.setThreadNamePrefix("bulk-pnl-task-");
        executor.initialize();
        return executor;
    }
}

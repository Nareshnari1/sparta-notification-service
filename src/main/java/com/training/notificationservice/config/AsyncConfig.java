package com.training.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@code @Async} and provides the executor used to dispatch
 * notifications off the HTTP request thread. Delivery (e.g. a blocking SMTP
 * send) can take seconds; running it on this pool lets the accept-side endpoint
 * respond immediately (202 Accepted) instead of holding the caller's connection
 * open for the whole send.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "notificationDispatchExecutor")
    public TaskExecutor notificationDispatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notif-dispatch-");
        executor.initialize();
        return executor;
    }
}
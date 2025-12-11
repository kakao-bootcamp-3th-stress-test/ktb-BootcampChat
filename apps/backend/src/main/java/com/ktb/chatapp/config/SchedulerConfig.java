package com.ktb.chatapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Scheduler configuration for background maintenance tasks.
 */
@Configuration
public class SchedulerConfig {

    @Bean(name = "socketIdleCleanupScheduler")
    public TaskScheduler socketIdleCleanupScheduler(
            @Value("${socketio.connection.cleanup-thread-pool-size:1}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(1, poolSize));
        scheduler.setThreadNamePrefix("socket-idle-cleaner-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }
}

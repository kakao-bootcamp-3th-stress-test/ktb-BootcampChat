package com.ktb.chatapp.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SocketAsyncConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService socketIoScheduler(
            @Value("${socketio.executor.pool-size:16}") int poolSize) {
        int resolvedSize = Math.max(4, poolSize);
        return Executors.newScheduledThreadPool(resolvedSize, r -> {
            Thread t = new Thread(r);
            t.setName("socket-io-async-" + t.getId());
            t.setDaemon(true);
            return t;
        });
    }
}

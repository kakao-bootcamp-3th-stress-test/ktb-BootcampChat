package com.ktb.chatapp.websocket.socketio.message.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.chatapp.dto.message.MessageResponse;
import com.ktb.chatapp.websocket.socketio.message.MessageDeliveryService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "chat.message.queue.mode", havingValue = "redis", matchIfMissing = true)
@RequiredArgsConstructor
public class RedisMessageQueueWorker {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MessageDeliveryService messageDeliveryService;

    @Value("${chat.message.redis.queue-key:chat:room:queue}")
    private String queueKey;

    @Value("${chat.message.redis.queue-workers:2}")
    private int workerCount;

    @Value("${chat.message.redis.queue-poll-timeout-seconds:2}")
    private long pollTimeoutSeconds;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executorService;
    private final List<Future<?>> workerFutures = new ArrayList<>();

    @PostConstruct
    public void start() {
        running.set(true);
        executorService = Executors.newFixedThreadPool(Math.max(workerCount, 1));
        for (int i = 0; i < Math.max(workerCount, 1); i++) {
            workerFutures.add(executorService.submit(this::pollLoop));
        }
        log.info("Redis message queue worker started with {} threads", Math.max(workerCount, 1));
    }

    private void pollLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                String payload = redisTemplate.opsForList()
                    .rightPop(queueKey, Duration.ofSeconds(Math.max(pollTimeoutSeconds, 1)));
                if (payload == null) {
                    continue;
                }
                MessageResponse response = objectMapper.readValue(payload, MessageResponse.class);
                messageDeliveryService.deliver(response);
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Redis message queue worker error", e);
                } else {
                    log.debug("Worker shutting down");
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (executorService != null) {
            executorService.shutdownNow();
        }
        workerFutures.clear();
    }
}

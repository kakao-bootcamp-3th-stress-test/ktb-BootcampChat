package com.ktb.chatapp.websocket.socketio.message.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.chatapp.dto.message.MessageResponse;
import com.ktb.chatapp.websocket.socketio.message.MessageDispatchQueue;
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
public class RedisMessageDispatchQueue implements MessageDispatchQueue {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${chat.message.redis.queue-key:chat:room:queue}")
    private String queueKey;

    @Override
    public void enqueue(MessageResponse response) {
        if (response == null || response.getRoomId() == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(response);
            redisTemplate.opsForList().leftPush(queueKey, payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to enqueue message for room {}: {}", response.getRoomId(), e.getMessage(), e);
        }
    }
}

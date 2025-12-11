package com.ktb.chatapp.websocket.socketio.message.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.chatapp.dto.message.MessageResponse;
import com.ktb.chatapp.websocket.socketio.message.MessageBroadcastStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub을 이용한 메시지 브로드캐스트 전략.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chat.message.broadcast.mode", havingValue = "redis", matchIfMissing = true)
public class RedisMessageBroadcastStrategy implements MessageBroadcastStrategy {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${chat.message.redis.channel-prefix:chat:room}")
    private String channelPrefix;

    @Override
    public void broadcast(MessageResponse response) {
        try {
            String payload = objectMapper.writeValueAsString(response);
            redisTemplate.convertAndSend(channel(response.getRoomId()), payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message for Redis broadcast: {}", e.getMessage(), e);
        }
    }

    private String channel(String roomId) {
        return channelPrefix + ":" + roomId;
    }
}

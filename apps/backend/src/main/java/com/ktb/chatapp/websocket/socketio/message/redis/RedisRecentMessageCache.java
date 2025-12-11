package com.ktb.chatapp.websocket.socketio.message.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.chatapp.dto.message.MessageResponse;
import com.ktb.chatapp.websocket.socketio.message.RecentMessageCache;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chat.message.broadcast.mode", havingValue = "redis", matchIfMissing = true)
public class RedisRecentMessageCache implements RecentMessageCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${chat.message.redis.cache-prefix:chat:room}")
    private String cachePrefix;

    @Value("${chat.message.redis.cache-size:100}")
    private int cacheSize;

    @Value("${chat.message.redis.cache-ttl-seconds:600}")
    private long cacheTtlSeconds;

    @Override
    public void cache(MessageResponse response) {
        if (response == null || response.getRoomId() == null) {
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(response);
            String key = cacheKey(response.getRoomId());
            redisTemplate.opsForList().leftPush(key, payload);
            redisTemplate.opsForList().trim(key, 0, Math.max(cacheSize, 1) - 1);
            redisTemplate.expire(key, Duration.ofSeconds(Math.max(cacheTtlSeconds, 60)));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize message for cache: {}", e.getMessage());
        }
    }

    @Override
    public Optional<CachedMessagesPage> getRecentMessages(String roomId, int limit) {
        if (roomId == null) {
            return Optional.empty();
        }
        String key = cacheKey(roomId);
        List<String> entries = redisTemplate.opsForList().range(key, 0, Math.max(limit, 1) - 1);
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }

        List<MessageResponse> responses = entries.stream()
            .map(this::deserialize)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(ArrayList::new));

        if (responses.isEmpty()) {
            return Optional.empty();
        }

        Collections.reverse(responses); // 최신 메시지가 앞에 있으므로 역순 정렬하여 ASC로 반환
        Long totalSize = redisTemplate.opsForList().size(key);
        boolean hasMore = totalSize != null && totalSize > responses.size();
        return Optional.of(new CachedMessagesPage(responses, hasMore));
    }

    private String cacheKey(String roomId) {
        return cachePrefix + ":" + roomId + ":recent";
    }

    private MessageResponse deserialize(String payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, MessageResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached message payload - discarding entry", e);
            return null;
        }
    }
}

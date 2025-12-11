package com.ktb.chatapp.websocket.socketio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed implementation of ChatDataStore for sharing socket state across nodes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "socketio.store", havingValue = "redis", matchIfMissing = true)
public class RedisChatDataStore implements ChatDataStore {

    private static final String KEY_PREFIX = "chatstore:";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${socketio.store.ttl-seconds:86400}")
    private long ttlSeconds;

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        String value = redisTemplate.opsForValue().get(buildKey(key));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, type));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize chat store entry for key {}. Removing corrupted data.", key, e);
            redisTemplate.delete(buildKey(key));
            return Optional.empty();
        }
    }

    @Override
    public void set(String key, Object value) {
        try {
            String payload = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(buildKey(key), payload, Duration.ofSeconds(Math.max(60, ttlSeconds)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize chat data for key " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(buildKey(key));
    }

    @Override
    public int size() {
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }

    private String buildKey(String key) {
        return KEY_PREFIX + key;
    }
}

package com.ktb.chatapp.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.chatapp.dto.user.UserResponse;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.user-cache.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "socketio.store", havingValue = "redis", matchIfMissing = true)
public class RedisUserProfileCache implements UserProfileCache {

    private static final String KEY_PREFIX = "userprofile:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.user-cache.ttl-seconds:600}")
    private long ttlSeconds;

    @Override
    public Optional<UserResponse> get(String userId) {
        if (userId == null) {
            return Optional.empty();
        }
        String payload = redisTemplate.opsForValue().get(buildKey(userId));
        if (payload == null) {
            return Optional.empty();
        }
        try {
            touchTtl(userId);
            return Optional.of(objectMapper.readValue(payload, UserResponse.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize user cache entry for {}. Evicting.", userId, e);
            redisTemplate.delete(buildKey(userId));
            return Optional.empty();
        }
    }

    @Override
    public void put(UserResponse userResponse) {
        if (userResponse == null || userResponse.getId() == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(userResponse);
            redisTemplate.opsForValue().set(buildKey(userResponse.getId()), payload, ttl());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize user cache entry for {}", userResponse.getId(), e);
        }
    }

    @Override
    public void evict(String userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.delete(buildKey(userId));
    }

    private void touchTtl(String userId) {
        redisTemplate.expire(buildKey(userId), ttl());
    }

    private Duration ttl() {
        return Duration.ofSeconds(Math.max(60, ttlSeconds));
    }

    private String buildKey(String userId) {
        return KEY_PREFIX + userId;
    }
}

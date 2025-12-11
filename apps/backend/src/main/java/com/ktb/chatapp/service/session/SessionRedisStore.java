package com.ktb.chatapp.service.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.chatapp.model.Session;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis implementation of SessionStore.
 * Stores session state as JSON with per-user keys.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.session", name = "store", havingValue = "redis")
@RequiredArgsConstructor
public class SessionRedisStore implements SessionStore {

    private static final String KEY_PREFIX = "session:";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<Session> findByUserId(String userId) {
        if (userId == null) {
            return Optional.empty();
        }
        String key = buildKey(userId);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, Session.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize session for userId {}. Removing corrupted entry.", userId, e);
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    @Override
    public Session save(Session session) {
        if (session.getUserId() == null) {
            throw new IllegalArgumentException("Session userId cannot be null");
        }
        try {
            String payload = objectMapper.writeValueAsString(session);
            long ttlSeconds = computeTtlSeconds(session);
            redisTemplate.opsForValue()
                    .set(buildKey(session.getUserId()), payload, Duration.ofSeconds(ttlSeconds));
            return session;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize session", e);
        }
    }

    @Override
    public void deleteAll(String userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.delete(buildKey(userId));
    }

    @Override
    public void delete(String userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }
        findByUserId(userId).ifPresent(existing -> {
            if (sessionId.equals(existing.getSessionId())) {
                deleteAll(userId);
            }
        });
    }

    private long computeTtlSeconds(Session session) {
        Instant expiresAt = session.getExpiresAt();
        if (expiresAt == null) {
            return 1800L; // default 30 minutes
        }
        long ttl = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(1L, ttl);
    }

    private String buildKey(String userId) {
        return KEY_PREFIX + userId;
    }
}

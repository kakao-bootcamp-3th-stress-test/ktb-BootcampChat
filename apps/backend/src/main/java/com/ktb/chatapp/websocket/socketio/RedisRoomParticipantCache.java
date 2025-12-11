package com.ktb.chatapp.websocket.socketio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.chatapp.dto.user.UserResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
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
@ConditionalOnProperty(name = "socketio.participants.cache-enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "socketio.store", havingValue = "redis", matchIfMissing = true)
public class RedisRoomParticipantCache implements RoomParticipantCache {

    private static final String KEY_PREFIX = "room:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${socketio.participants.ttl-seconds:300}")
    private long ttlSeconds;

    @Override
    public List<UserResponse> getParticipants(String roomId, Supplier<List<UserResponse>> loader) {
        var cachedEntries = redisTemplate.opsForHash().entries(participantKey(roomId));
        if (cachedEntries != null && !cachedEntries.isEmpty()) {
            touchTtl(roomId);
            return cachedEntries.values().stream()
                    .map(value -> deserialize(roomId, value))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        List<UserResponse> loaded = loader.get();
        if (loaded == null) {
            return Collections.emptyList();
        }

        loaded.forEach(user -> put(roomId, user));
        return loaded;
    }

    @Override
    public void addParticipant(String roomId, UserResponse participant) {
        if (participant == null) {
            return;
        }
        put(roomId, participant);
    }

    @Override
    public void removeParticipant(String roomId, String userId) {
        redisTemplate.opsForHash().delete(participantKey(roomId), userId);
        touchTtl(roomId);
    }

    @Override
    public void evict(String roomId) {
        redisTemplate.delete(participantKey(roomId));
    }

    private void put(String roomId, UserResponse participant) {
        try {
            redisTemplate.opsForHash().put(participantKey(roomId), participant.getId(), objectMapper.writeValueAsString(participant));
            touchTtl(roomId);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache participant {} for room {}", participant.getId(), roomId, e);
        }
    }

    private String participantKey(String roomId) {
        return KEY_PREFIX + roomId + ":participants";
    }

    private UserResponse deserialize(String roomId, Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.readValue(payload.toString(), UserResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize participant cache for room {}. Payload will be discarded.", roomId, e);
            return null;
        }
    }

    private void touchTtl(String roomId) {
        long ttl = Math.max(ttlSeconds, 60);
        redisTemplate.expire(participantKey(roomId), Duration.ofSeconds(ttl));
    }
}

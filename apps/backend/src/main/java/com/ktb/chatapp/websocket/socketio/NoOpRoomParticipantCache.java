package com.ktb.chatapp.websocket.socketio;

import com.ktb.chatapp.dto.user.UserResponse;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Fallback cache implementation used when Redis caching is disabled.
 */
@Component
@ConditionalOnMissingBean(RoomParticipantCache.class)
public class NoOpRoomParticipantCache implements RoomParticipantCache {

    @Override
    public List<UserResponse> getParticipants(String roomId, Supplier<List<UserResponse>> loader) {
        return loader.get();
    }

    @Override
    public void addParticipant(String roomId, UserResponse participant) {
        // no-op
    }

    @Override
    public void removeParticipant(String roomId, String userId) {
        // no-op
    }

    @Override
    public void evict(String roomId) {
        // no-op
    }
}

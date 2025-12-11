package com.ktb.chatapp.websocket.socketio;

import com.ktb.chatapp.dto.user.UserResponse;
import java.util.List;
import java.util.function.Supplier;

public interface RoomParticipantCache {

    List<UserResponse> getParticipants(String roomId, Supplier<List<UserResponse>> loader);

    void addParticipant(String roomId, UserResponse participant);

    void removeParticipant(String roomId, String userId);

    void evict(String roomId);
}

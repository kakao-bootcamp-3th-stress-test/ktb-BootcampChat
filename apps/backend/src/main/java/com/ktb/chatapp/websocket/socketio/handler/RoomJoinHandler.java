package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.ktb.chatapp.dto.message.FetchMessagesRequest;
import com.ktb.chatapp.dto.message.FetchMessagesResponse;
import com.ktb.chatapp.dto.rooms.JoinRoomSuccessResponse;
import com.ktb.chatapp.dto.user.UserResponse;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.MessageType;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.websocket.socketio.RoomParticipantCache;
import com.ktb.chatapp.websocket.socketio.SocketConnectionTracker;
import com.ktb.chatapp.websocket.socketio.SocketUser;
import com.ktb.chatapp.websocket.socketio.UserRooms;
import com.ktb.chatapp.websocket.socketio.message.MessageDispatchQueue;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.*;

/**
 * 방 입장 처리 핸들러
 * 채팅방 입장, 참가자 관리, 초기 메시지 로드 담당
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RoomJoinHandler {

    private final SocketIOServer socketIOServer;
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final UserRooms userRooms;
    private final MessageLoader messageLoader;
    private final MessageResponseMapper messageResponseMapper;
    private final SocketConnectionTracker connectionTracker;
    private final RoomParticipantCache participantCache;
    private final MessageDispatchQueue messageDispatchQueue;
    
    @OnEvent(JOIN_ROOM)
    public void handleJoinRoom(SocketIOClient client, String roomId) {
        try {
            connectionTracker.touch(client);
            String userId = getUserId(client);
            String userName = getUserName(client);

            if (userId == null) {
                client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "Unauthorized"));
                return;
            }
            
            Optional<User> userOpt;
            try {
                userOpt = userRepository.findById(userId);
            } catch (Exception dbException) {
                log.error("Database error during joinRoom for user {}: {}", userId, dbException.getMessage());
                client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "데이터베이스 연결 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
                return;
            }
            
            if (userOpt.isEmpty()) {
                client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "User not found"));
                return;
            }
            var user = userOpt.get();
            var userResponse = UserResponse.from(user);

            Optional<Room> roomOpt;
            try {
                roomOpt = roomRepository.findById(roomId);
            } catch (Exception dbException) {
                log.error("Database error during joinRoom for room {}: {}", roomId, dbException.getMessage());
                client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "데이터베이스 연결 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
                return;
            }
            
            if (roomOpt.isEmpty()) {
                client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "채팅방을 찾을 수 없습니다."));
                return;
            }
            
            // 이미 해당 방에 참여 중인지 확인
            if (userRooms.isInRoom(userId, roomId)) {
                log.debug("User {} already in room {}", userId, roomId);
                client.joinRoom(roomId);
                client.sendEvent(JOIN_ROOM_SUCCESS, Map.of("roomId", roomId));
                return;
            }

            // MongoDB의 $addToSet 연산자를 사용한 원자적 업데이트
            try {
                roomRepository.addParticipant(roomId, userId);
            } catch (Exception dbException) {
                log.error("Database error adding participant {} to room {}: {}", userId, roomId, dbException.getMessage());
                client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "데이터베이스 연결 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
                return;
            }

            // Join socket room and add to user's room set
            client.joinRoom(roomId);
            userRooms.add(userId, roomId);
            participantCache.addParticipant(roomId, userResponse);

            Message joinMessage = Message.builder()
                .roomId(roomId)
                .content(userName + "님이 입장하였습니다.")
                .type(MessageType.system)
                .timestamp(LocalDateTime.now())
                .mentions(new ArrayList<>())
                .isDeleted(false)
                .reactions(new HashMap<>())
                .readers(new ArrayList<>())
                .metadata(new HashMap<>())
                .build();

            try {
                joinMessage = messageRepository.save(joinMessage);
            } catch (Exception dbException) {
                log.error("Database error saving join message for room {}: {}", roomId, dbException.getMessage());
                // 메시지 저장 실패는 치명적이지 않으므로 계속 진행
                joinMessage = null;
            }

            // 초기 메시지 로드
            FetchMessagesRequest req = new FetchMessagesRequest(roomId, 30, null);
            FetchMessagesResponse messageLoadResult;
            try {
                messageLoadResult = messageLoader.loadMessages(req, userId);
            } catch (Exception dbException) {
                log.error("Database error loading messages for room {}: {}", roomId, dbException.getMessage());
                // 메시지 로드 실패 시 빈 응답으로 계속 진행
                messageLoadResult = new FetchMessagesResponse(Collections.emptyList(), false);
            }

            List<UserResponse> participants;
            try {
                participants = participantCache.getParticipants(roomId, () -> loadParticipantsFromDb(roomId));
            } catch (Exception dbException) {
                log.error("Database error loading participants for room {}: {}", roomId, dbException.getMessage());
                // 참가자 로드 실패 시 빈 리스트로 계속 진행
                participants = Collections.emptyList();
            }
            
            JoinRoomSuccessResponse response = JoinRoomSuccessResponse.builder()
                .roomId(roomId)
                .participants(participants)
                .messages(messageLoadResult.getMessages())
                .hasMore(messageLoadResult.isHasMore())
                .activeStreams(Collections.emptyList())
                .build();

            client.sendEvent(JOIN_ROOM_SUCCESS, response);

            // 입장 메시지 브로드캐스트 (메시지가 저장된 경우에만)
            if (joinMessage != null) {
                try {
                    messageDispatchQueue.enqueue(messageResponseMapper.mapToMessageResponse(joinMessage));
                } catch (Exception queueException) {
                    log.warn("Failed to enqueue join message for room {}: {}", roomId, queueException.getMessage());
                    // 큐 실패는 치명적이지 않으므로 계속 진행
                }
            }

            // 참가자 목록 업데이트 브로드캐스트
            socketIOServer.getRoomOperations(roomId)
                .sendEvent(PARTICIPANTS_UPDATE, participants);

            log.info("User {} joined room {} successfully. Message count: {}, hasMore: {}",
                userName, roomId, messageLoadResult.getMessages().size(), messageLoadResult.isHasMore());

        } catch (Exception e) {
            log.error("Error handling joinRoom", e);
            client.sendEvent(JOIN_ROOM_ERROR, Map.of(
                "message", e.getMessage() != null ? e.getMessage() : "채팅방 입장에 실패했습니다."
            ));
        }
    }

    public void restoreExistingMembership(SocketIOClient client, String roomId) {
        try {
            connectionTracker.touch(client);
            String userId = getUserId(client);
            if (userId == null) {
                return;
            }
            client.joinRoom(roomId);
            if (!userRooms.isInRoom(userId, roomId)) {
                userRooms.add(userId, roomId);
            }
            log.debug("Restored room {} for user {}", roomId, userId);
        } catch (Exception e) {
            log.warn("Failed to restore room {}: {}", roomId, e.getMessage());
        }
    }
    
    private SocketUser getUser(SocketIOClient client) {
        return client.get("user");
    }

    private String getUserId(SocketIOClient client) {
        SocketUser user = getUser(client);
        return user != null ? user.id() : null;
    }

    private String getUserName(SocketIOClient client) {
        SocketUser user = getUser(client);
        return user != null ? user.name() : null;
    }

    private List<UserResponse> loadParticipantsFromDb(String roomId) {
        try {
            Optional<Room> roomOpt = roomRepository.findById(roomId);
            if (roomOpt.isEmpty()) {
                return List.of();
            }
            Set<String> participantIdSet = roomOpt.get().getParticipantIds();
            if (participantIdSet == null || participantIdSet.isEmpty()) {
                return List.of();
            }
            List<String> participantIds = new ArrayList<>(participantIdSet);
            return userRepository.findAllById(participantIds).stream()
                    .map(UserResponse::from)
                    .toList();
        } catch (Exception dbException) {
            log.error("Database error loading participants from DB for room {}: {}", roomId, dbException.getMessage());
            return List.of();
        }
    }
}

package com.ktb.chatapp.websocket.socketio.message;

import com.corundumstudio.socketio.SocketIOServer;
import com.ktb.chatapp.dto.message.MessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.MESSAGE;

/**
 * Redis를 사용하지 않는 환경을 위한 직접 브로드캐스트 전략.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chat.message.broadcast.mode", havingValue = "direct")
public class DirectMessageBroadcastStrategy implements MessageBroadcastStrategy {

    private final SocketIOServer socketIOServer;

    @Override
    public void broadcast(MessageResponse response) {
        try {
            socketIOServer.getRoomOperations(response.getRoomId())
                .sendEvent(MESSAGE, response);
        } catch (Exception e) {
            log.error("Direct broadcast failed for room {}", response.getRoomId(), e);
        }
    }
}

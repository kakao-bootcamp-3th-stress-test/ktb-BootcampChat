package com.ktb.chatapp.websocket.socketio;

import com.corundumstudio.socketio.SocketIOServer;
import com.ktb.chatapp.dto.message.MessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.MESSAGE;

/**
 * Redis Pub/Sub 을 사용하지 않고,
 * 현재 인스턴스의 Socket.IO 서버로 직접 브로드캐스트하는 기본 구현체.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.pubsub-redis.enabled", havingValue = "false", matchIfMissing = true)
public class LocalSocketIOMessagePublisher implements ChatMessagePublisher {

    private final SocketIOServer socketIOServer;

    @Override
    public void publish(MessageResponse messageResponse) {
        if (messageResponse == null || messageResponse.getRoomId() == null) {
            log.warn("Skipping publish of null/invalid messageResponse: {}", messageResponse);
            return;
        }
        socketIOServer.getRoomOperations(messageResponse.getRoomId())
                .sendEvent(MESSAGE, messageResponse);
    }
}


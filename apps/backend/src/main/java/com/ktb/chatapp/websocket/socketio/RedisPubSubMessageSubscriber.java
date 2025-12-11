package com.ktb.chatapp.websocket.socketio;

import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.chatapp.dto.message.MessageResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.ktb.chatapp.websocket.socketio.RedisPubSubMessagePublisher.CHAT_MESSAGES_CHANNEL;
import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.MESSAGE;

/**
 * Redis Pub/Sub 으로부터 채팅 메시지를 구독하여
 * 현재 인스턴스의 Socket.IO 서버로 브로드캐스트하는 서브스크라이버.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RedissonClient.class)
@ConditionalOnProperty(name = "app.pubsub-redis.enabled", havingValue = "true")
public class RedisPubSubMessageSubscriber {

    private final RedissonClient pubSubRedissonClient;
    private final ObjectMapper objectMapper;
    private final SocketIOServer socketIOServer;

    @PostConstruct
    public void subscribe() {
        RTopic topic = pubSubRedissonClient.getTopic(CHAT_MESSAGES_CHANNEL);
        topic.addListener(String.class, (channel, payload) -> {
            try {
                MessageResponse messageResponse =
                        objectMapper.readValue(payload, MessageResponse.class);
                if (messageResponse.getRoomId() == null) {
                    log.warn("Received pub/sub message without roomId, skipping: {}", messageResponse);
                    return;
                }
                socketIOServer.getRoomOperations(messageResponse.getRoomId())
                        .sendEvent(MESSAGE, messageResponse);
            } catch (Exception e) {
                log.error("Failed to handle pub/sub chat message payload", e);
            }
        });
        log.info("Subscribed to Redis pub/sub channel for chat messages: {}", CHAT_MESSAGES_CHANNEL);
    }
}


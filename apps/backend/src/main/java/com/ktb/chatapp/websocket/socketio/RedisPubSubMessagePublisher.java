package com.ktb.chatapp.websocket.socketio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.chatapp.dto.message.MessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 을 이용해 채팅 메시지를 퍼블리시하는 구현체.
 *
 * - 퍼블리시만 담당하고, 실제 Socket.IO 브로드캐스트는 {@link RedisPubSubMessageSubscriber} 가 수행한다.
 * - Pub/Sub Redis 인스턴스를 별도로 둘 수 있도록, PubSubRedisConfig 에서 생성한 RedissonClient 를 사용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(name = "pubSubRedissonClient")
@ConditionalOnProperty(name = "app.pubsub-redis.enabled", havingValue = "true")
public class RedisPubSubMessagePublisher implements ChatMessagePublisher {

    /**
     * 채팅 메시지 Pub/Sub 채널 이름.
     * 방 정보는 payload 내 roomId 로 구분한다.
     */
    public static final String CHAT_MESSAGES_CHANNEL = "chat:messages";

    @Qualifier("pubSubRedissonClient")
    private final RedissonClient pubSubRedissonClient;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(MessageResponse messageResponse) {
        if (messageResponse == null || messageResponse.getRoomId() == null) {
            log.warn("Skipping publish of null/invalid messageResponse: {}", messageResponse);
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(messageResponse);
            RTopic topic = pubSubRedissonClient.getTopic(CHAT_MESSAGES_CHANNEL);
            long receivers = topic.publish(payload);
            log.debug("Published chat message to Redis pub/sub. roomId={}, receivers={}",
                    messageResponse.getRoomId(), receivers);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize MessageResponse for pub/sub", e);
        } catch (Exception e) {
            log.error("Failed to publish chat message to Redis pub/sub", e);
        }
    }
}


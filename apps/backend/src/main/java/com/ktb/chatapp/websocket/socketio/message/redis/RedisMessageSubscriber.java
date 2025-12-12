package com.ktb.chatapp.websocket.socketio.message.redis;

import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.chatapp.dto.message.MessageResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.MESSAGE;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chat.message.broadcast.mode", havingValue = "redis", matchIfMissing = true)
public class RedisMessageSubscriber implements MessageListener {

    private final RedisMessageListenerContainer listenerContainer;
    private final SocketIOServer socketIOServer;
    private final ObjectMapper objectMapper;

    @Value("${chat.message.redis.channel-prefix:chat:room}")
    private String channelPrefix;

    @PostConstruct
    public void subscribe() {
        listenerContainer.addMessageListener(this, new PatternTopic(channelPrefix + ":*"));
        log.info("Subscribed to Redis chat channels with prefix {}", channelPrefix);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            MessageResponse response = objectMapper.readValue(message.getBody(), MessageResponse.class);
            socketIOServer.getRoomOperations(response.getRoomId())
                .sendEvent(MESSAGE, response);
        } catch (Exception e) {
            log.error("Failed to process Redis chat message", e);
        }
    }
}

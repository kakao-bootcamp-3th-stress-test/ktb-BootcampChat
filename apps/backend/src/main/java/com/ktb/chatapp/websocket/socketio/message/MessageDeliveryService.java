package com.ktb.chatapp.websocket.socketio.message;

import com.ktb.chatapp.dto.message.MessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 메시지 캐시와 브로드캐스트를 조율하는 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageDeliveryService {

    private final MessageBroadcastStrategy broadcastStrategy;
    private final RecentMessageCache recentMessageCache;

    public void deliver(MessageResponse response) {
        if (response == null || response.getRoomId() == null) {
            log.debug("Ignored delivery for empty response");
            return;
        }

        try {
            recentMessageCache.cache(response);
        } catch (Exception e) {
            log.warn("Failed to cache message for room {}: {}", response.getRoomId(), e.getMessage());
        }

        broadcastStrategy.broadcast(response);
    }
}

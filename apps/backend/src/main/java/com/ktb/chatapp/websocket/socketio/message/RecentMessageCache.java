package com.ktb.chatapp.websocket.socketio.message;

import com.ktb.chatapp.dto.message.MessageResponse;
import java.util.List;
import java.util.Optional;

/**
 * 최근 메시지를 캐시하여 빠른 초기 로드를 지원하는 인터페이스.
 */
public interface RecentMessageCache {

    void cache(MessageResponse response);

    Optional<CachedMessagesPage> getRecentMessages(String roomId, int limit);

    record CachedMessagesPage(List<MessageResponse> messages, boolean hasMore) {
    }
}

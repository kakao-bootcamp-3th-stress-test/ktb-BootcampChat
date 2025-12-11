package com.ktb.chatapp.websocket.socketio.message;

import com.ktb.chatapp.dto.message.MessageResponse;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(RecentMessageCache.class)
public class NoOpRecentMessageCache implements RecentMessageCache {

    @Override
    public void cache(MessageResponse response) {
        // no-op
    }

    @Override
    public Optional<CachedMessagesPage> getRecentMessages(String roomId, int limit) {
        return Optional.empty();
    }
}

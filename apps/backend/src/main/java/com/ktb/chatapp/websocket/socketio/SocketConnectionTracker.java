package com.ktb.chatapp.websocket.socketio;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.SESSION_ENDED;

/**
 * Tracks active socket connections and closes idle or lost sessions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
public class SocketConnectionTracker {

    private final SocketIOServer socketIOServer;
    private final MeterRegistry meterRegistry;

    @Value("${socketio.connection.max-idle-ms:300000}")
    private long maxIdleMs;

    private final ConcurrentHashMap<String, ConnectionInfo> connections = new ConcurrentHashMap<>();

    public void register(SocketIOClient client, SocketUser user) {
        connections.put(client.getSessionId().toString(),
            new ConnectionInfo(user.id(), client.getSessionId().toString()));
    }

    public void unregister(SocketIOClient client) {
        if (client == null) {
            return;
        }
        connections.remove(client.getSessionId().toString());
    }

    public void touch(SocketIOClient client) {
        if (client == null) {
            return;
        }
        ConnectionInfo info = connections.get(client.getSessionId().toString());
        if (info != null) {
            info.touch();
        }
    }

    @Scheduled(
            fixedDelayString = "${socketio.connection.cleanup-interval-ms:60000}",
            scheduler = "socketIdleCleanupScheduler")
    public void cleanupIdleConnections() {
        Timer.Sample sample = Timer.start(meterRegistry);
        long now = System.currentTimeMillis();
        try {
            connections.forEach((socketId, info) -> {
                if (now - info.getLastSeen() > maxIdleMs) {
                    log.warn("Closing idle socket {} for user {} (idle {} sec)",
                            socketId, info.getUserId(), Duration.ofMillis(now - info.getLastSeen()).toSeconds());
                    connections.remove(socketId);
                    var client = socketIOServer.getClient(UUID.fromString(socketId));
                    if (client != null) {
                        client.sendEvent(SESSION_ENDED, Map.of(
                                "reason", "idle_timeout",
                                "message", "장시간 활동이 없어 연결이 종료되었습니다."
                        ));
                        client.disconnect();
                    }
                }
            });
        } finally {
            sample.stop(meterRegistry.timer("socketio.connection.cleanup.time"));
        }
    }

    private static final class ConnectionInfo {
        private final String userId;
        private final String socketId;
        private volatile long lastSeen;

        private ConnectionInfo(String userId, String socketId) {
            this.userId = userId;
            this.socketId = socketId;
            this.lastSeen = System.currentTimeMillis();
        }

        private void touch() {
            this.lastSeen = System.currentTimeMillis();
        }

        private long getLastSeen() {
            return lastSeen;
        }

        private String getUserId() {
            return userId;
        }
    }
}

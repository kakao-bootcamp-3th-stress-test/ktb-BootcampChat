package com.ktb.chatapp.websocket.socketio;

import com.corundumstudio.socketio.AuthTokenListener;
import com.corundumstudio.socketio.AuthTokenResult;
import com.corundumstudio.socketio.SocketIOClient;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.JwtService;
import com.ktb.chatapp.service.UserLookupService;
import com.ktb.chatapp.websocket.socketio.handler.ConnectionLoginHandler;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Socket.IO Authorization Handler
 * socket.handshake.auth.token 값을 검증한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AuthTokenListenerImpl implements AuthTokenListener {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectProvider<ConnectionLoginHandler> socketIOChatHandlerProvider;
    private final UserLookupService userLookupService;

    @Override
    public AuthTokenResult getAuthTokenResult(Object _authToken, SocketIOClient client) {
        try {
            var authToken = (Map<?, ?>) _authToken;
            String token = authToken.get("token") != null ? authToken.get("token").toString() : null;
            if (token == null) {
                log.warn("Missing authentication token in Socket.IO handshake");
                return new AuthTokenResult(false, "Authentication required");
            }

            String userId;
            try {
                userId = jwtService.extractUserId(token);
            } catch (JwtException e) {
                return new AuthTokenResult(false, Map.of("message", "Invalid token"));
            }

            // Load user from database
            User user;
            try {
                user = userRepository.findById(userId).orElse(null);
            } catch (Exception dbException) {
                log.error("Database error during Socket.IO authentication for user {}: {}", userId, dbException.getMessage());
                // MongoDB 연결 실패 시에도 인증은 실패 처리하되, 서버는 계속 실행되도록 함
                return new AuthTokenResult(false, Map.of("message", "Database connection error. Please try again later."));
            }
            
            if (user == null) {
                log.error("User not found: {}", userId);
                return new AuthTokenResult(false, Map.of("message", "User not found"));
            }

            log.info("Socket.IO connection authorized for user: {} ({})", user.getName(), userId);
            try {
                userLookupService.cacheUser(user);
            } catch (Exception cacheException) {
                log.warn("Failed to cache user {}: {}", userId, cacheException.getMessage());
                // 캐시 실패는 치명적이지 않으므로 계속 진행
            }
            
            var socketUser = new SocketUser(user.getId(), user.getName(), client.getSessionId().toString());
            try {
                socketIOChatHandlerProvider.getObject().onConnect(client, socketUser);
            } catch (Exception connectException) {
                log.error("Error during Socket.IO connection setup for user {}: {}", userId, connectException.getMessage());
                // 연결 설정 실패 시에도 인증은 성공으로 처리하되, 클라이언트에게 알림
                return new AuthTokenResult(false, Map.of("message", "Connection setup failed. Please reconnect."));
            }
            return AuthTokenResult.AuthTokenResultSuccess;
        } catch (Exception e) {
            log.error("Socket.IO authentication error: {}", e.getMessage(), e);
            return new AuthTokenResult(false, Map.of("message", e.getMessage()));
        }
    }
}

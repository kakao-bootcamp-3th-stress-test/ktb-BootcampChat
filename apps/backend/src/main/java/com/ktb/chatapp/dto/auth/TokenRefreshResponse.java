package com.ktb.chatapp.dto.auth;

public record TokenRefreshResponse(
    boolean success,
    String message,
    String token,
    String sessionId
) {
}

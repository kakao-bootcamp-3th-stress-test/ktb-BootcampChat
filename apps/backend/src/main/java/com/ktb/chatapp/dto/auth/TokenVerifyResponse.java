package com.ktb.chatapp.dto.auth;

public record TokenVerifyResponse(
    boolean success,
    String message,
    AuthUserDto user
) {
}

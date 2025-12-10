package com.ktb.chatapp.controller.user;

import com.ktb.chatapp.dto.user.UserResponse;
import lombok.Data;

@Data
class UserUpdateResponse {
    private final boolean success = true;
    private final String message;
    private final UserResponse user;
    
    UserUpdateResponse(String message, UserResponse user) {
        this.message = message;
        this.user = user;
    }
}

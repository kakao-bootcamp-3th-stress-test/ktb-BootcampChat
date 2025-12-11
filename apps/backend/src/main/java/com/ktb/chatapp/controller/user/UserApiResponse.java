package com.ktb.chatapp.controller.user;

import com.ktb.chatapp.dto.user.UserResponse;
import lombok.Data;

@Data
class UserApiResponse {
    private boolean success;
    private UserResponse user;
    
    public UserApiResponse(UserResponse user) {
        this.user = user;
        this.success = true;
    }
}

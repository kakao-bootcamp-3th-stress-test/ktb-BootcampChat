package com.ktb.chatapp.dto.user;

import com.ktb.chatapp.dto.auth.AuthUserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private boolean success;
    private String token;
    private AuthUserDto user;
    private String message;
}

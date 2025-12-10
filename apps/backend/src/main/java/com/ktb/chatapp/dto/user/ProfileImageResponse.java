package com.ktb.chatapp.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProfileImageResponse {
    private boolean success;
    private String message;
    private String imageUrl;
}

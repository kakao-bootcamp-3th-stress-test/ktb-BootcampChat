package com.ktb.chatapp.cache;

import com.ktb.chatapp.dto.user.UserResponse;
import java.util.Optional;

public interface UserProfileCache {

    Optional<UserResponse> get(String userId);

    void put(UserResponse userResponse);

    void evict(String userId);
}

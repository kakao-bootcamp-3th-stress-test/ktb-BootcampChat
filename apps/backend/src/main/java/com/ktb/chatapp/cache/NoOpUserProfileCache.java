package com.ktb.chatapp.cache;

import com.ktb.chatapp.dto.user.UserResponse;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(UserProfileCache.class)
public class NoOpUserProfileCache implements UserProfileCache {

    @Override
    public Optional<UserResponse> get(String userId) {
        return Optional.empty();
    }

    @Override
    public void put(UserResponse userResponse) {
        // no-op
    }

    @Override
    public void evict(String userId) {
        // no-op
    }
}

package com.ktb.chatapp.service;

import com.ktb.chatapp.cache.UserProfileCache;
import com.ktb.chatapp.dto.user.UserResponse;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserLookupService {

    private final UserRepository userRepository;
    private final UserProfileCache userProfileCache;

    public Optional<UserResponse> findUser(String userId) {
        if (userId == null) {
            return Optional.empty();
        }
        Optional<UserResponse> cached = userProfileCache.get(userId);
        if (cached.isPresent()) {
            return cached;
        }
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .map(profile -> {
                    userProfileCache.put(profile);
                    return profile;
                });
    }

    public void cacheUser(User user) {
        if (user == null) {
            return;
        }
        userProfileCache.put(UserResponse.from(user));
    }

    public void evict(String userId) {
        userProfileCache.evict(userId);
    }
}

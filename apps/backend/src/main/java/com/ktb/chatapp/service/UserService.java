package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.user.ProfileImageResponse;
import com.ktb.chatapp.dto.user.UpdateProfileRequest;
import com.ktb.chatapp.dto.user.UserResponse;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final FileService fileService;

    @Value("${app.profile.image.max-size:5242880}") // 5MB
    private long maxProfileImageSize;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp"
    );

    /**
     * 현재 사용자 프로필 조회
     * @param email 사용자 이메일
     */
    public UserResponse getCurrentUserProfile(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }

    /**
     * 사용자 프로필 업데이트
     * @param email 사용자 이메일
     */
    public UserResponse updateUserProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        // 프로필 정보 업데이트
        user.setName(request.getName());
        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);
        log.info("사용자 프로필 업데이트 완료 - ID: {}, Name: {}", user.getId(), request.getName());

        return UserResponse.from(updatedUser);
    }

    /**
     * 프로필 이미지 업로드
     * @param email 사용자 이메일
     */
    public ProfileImageResponse uploadProfileImage(String email, MultipartFile file) {
        try {
            // 사용자 조회
            User user = userRepository.findByEmail(email.toLowerCase())
                    .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

            // 파일 유효성 검증
            validateProfileImageFile(file);

            // 기존 프로필 이미지 삭제
            if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
                deleteOldProfileImage(user.getProfileImage());
            }

            // 새 파일 저장 (보안 검증 포함)
            String profileImageUrl = fileService.storeFile(file, "profiles");
            
            if (profileImageUrl == null || profileImageUrl.isEmpty()) {
                log.error("프로필 이미지 URL이 생성되지 않았습니다 - User: {}", email);
                throw new RuntimeException("프로필 이미지 URL이 생성되지 않았습니다.");
            }

            // 사용자 프로필 이미지 URL 업데이트
            user.setProfileImage(profileImageUrl);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("프로필 이미지 업로드 완료 - User ID: {}, File: {}", user.getId(), profileImageUrl);

            return new ProfileImageResponse(
                    true,
                    "프로필 이미지가 업데이트되었습니다.",
                    profileImageUrl
            );
        } catch (UsernameNotFoundException e) {
            log.error("프로필 이미지 업로드 실패 - 사용자 없음: {}", email);
            throw e;
        } catch (IllegalArgumentException e) {
            log.error("프로필 이미지 업로드 실패 - 유효성 검증 실패: {}", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.error("프로필 이미지 업로드 실패 - 저장 오류: {}", e.getMessage(), e);
            throw new RuntimeException("프로필 이미지 업로드에 실패했습니다: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("프로필 이미지 업로드 중 예기치 않은 오류: {}", e.getMessage(), e);
            throw new RuntimeException("프로필 이미지 업로드 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 프론트엔드에서 직접 S3에 업로드한 프로필 이미지의 메타데이터 저장
     * 프론트엔드에서 이미 검증 완료했으므로 파일 없이 메타데이터만 저장 (속도 최적화)
     * @param email 사용자 이메일
     * @param s3Key S3 키
     * @param s3Url S3 URL (CloudFront URL)
     */
    public ProfileImageResponse saveProfileImageMetadata(String email, String s3Key, String s3Url) {
        try {
            // 사용자 조회
            User user = userRepository.findByEmail(email.toLowerCase())
                    .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

            // 필수 파라미터 검증
            if (s3Url == null || s3Url.isEmpty()) {
                log.error("프로필 이미지 URL이 제공되지 않았습니다 - User: {}, s3Key: {}", email, s3Key);
                throw new RuntimeException("프로필 이미지 URL이 제공되지 않았습니다.");
            }

            // 기존 프로필 이미지 삭제
            if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
                deleteOldProfileImage(user.getProfileImage());
            }

            // 사용자 프로필 이미지 URL 업데이트
            user.setProfileImage(s3Url);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("프로필 이미지 메타데이터 저장 완료 - User ID: {}, S3Key: {}, URL: {}", user.getId(), s3Key, s3Url);

            return new ProfileImageResponse(
                    true,
                    "프로필 이미지가 업데이트되었습니다.",
                    s3Url
            );
        } catch (UsernameNotFoundException e) {
            log.error("프로필 이미지 메타데이터 저장 실패 - 사용자 없음: {}", email);
            throw e;
        } catch (RuntimeException e) {
            log.error("프로필 이미지 메타데이터 저장 실패: {}", e.getMessage(), e);
            throw new RuntimeException("프로필 이미지 메타데이터 저장에 실패했습니다: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("프로필 이미지 메타데이터 저장 중 예기치 않은 오류: {}", e.getMessage(), e);
            throw new RuntimeException("프로필 이미지 메타데이터 저장 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 특정 사용자 프로필 조회
     */
    public UserResponse getUserProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        return UserResponse.from(user);
    }

    /**
     * 프로필 이미지 파일 유효성 검증
     */
    private void validateProfileImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("이미지가 제공되지 않았습니다.");
        }

        // 파일 크기 검증
        if (file.getSize() > maxProfileImageSize) {
            throw new IllegalArgumentException("파일 크기는 5MB를 초과할 수 없습니다.");
        }

        // Content-Type 검증
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        // 파일 확장자 검증 (보안을 위해 화이트리스트 유지)
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        // FileSecurityUtil의 static 메서드 호출
        String extension = FileUtil.getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }
    }

    /**
     * 기존 프로필 이미지 삭제
     */
    private void deleteOldProfileImage(String profileImageUrl) {
        try {
            fileService.deleteStoredFile(profileImageUrl);
        } catch (Exception e) {
            log.warn("기존 프로필 이미지 삭제 실패: {}", e.getMessage());
        }
    }

    /**
     * 프로필 이미지 삭제
     * @param email 사용자 이메일
     */
    public void deleteProfileImage(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            deleteOldProfileImage(user.getProfileImage());
            user.setProfileImage("");
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("프로필 이미지 삭제 완료 - User ID: {}", user.getId());
        }
    }

    /**
     * 회원 탈퇴 처리
     * @param email 사용자 이메일
     */
    public void deleteUserAccount(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            deleteOldProfileImage(user.getProfileImage());
        }

        userRepository.delete(user);
        log.info("회원 탈퇴 완료 - User ID: {}", user.getId());
    }
}

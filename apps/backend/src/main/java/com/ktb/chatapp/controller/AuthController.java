package com.ktb.chatapp.controller;

import com.ktb.chatapp.dto.*;
import com.ktb.chatapp.dto.auth.AuthUserDto;
import com.ktb.chatapp.dto.auth.TokenRefreshResponse;
import com.ktb.chatapp.dto.auth.TokenVerifyResponse;
import com.ktb.chatapp.dto.user.LoginRequest;
import com.ktb.chatapp.dto.user.LoginResponse;
import com.ktb.chatapp.dto.user.RegisterRequest;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증 (Authentication)", description = "사용자 인증 관련 API - 회원가입, 로그인, 로그아웃, 토큰 관리")
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Operation(summary = "인증 API 상태 확인", description = "인증 API의 사용 가능한 엔드포인트 목록을 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "API 상태 정보 조회 성공")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false)
    @SecurityRequirement(name = "")
    @GetMapping
    public ResponseEntity<?> getAuthStatus() {
        Map<String, String> routes = new LinkedHashMap<>();
        routes.put("/register", "POST - 새 사용자 등록");
        routes.put("/login", "POST - 사용자 로그인");
        routes.put("/logout", "POST - 로그아웃 (인증 필요)");
        routes.put("/verify-token", "POST - 토큰 검증");
        routes.put("/refresh-token", "POST - 토큰 갱신 (인증 필요)");
        return ResponseEntity.ok(Map.of("status", "active", "routes", routes));
    }

    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "회원가입 성공",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 입력값",
            content = @Content(schema = @Schema(implementation = StandardResponse.class),
                examples = @ExampleObject(value = "{\"success\":false,\"code\":\"VALIDATION_ERROR\",\"errors\":[{\"field\":\"email\",\"message\":\"올바른 이메일 형식이 아닙니다.\"}]}"))),
        @ApiResponse(responseCode = "409", description = "이미 등록된 이메일",
            content = @Content(schema = @Schema(implementation = StandardResponse.class),
                examples = @ExampleObject(value = "{\"success\":false,\"message\":\"이미 등록된 이메일입니다.\"}"))),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = StandardResponse.class)))
    })
    @SecurityRequirement(name = "")
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(
            @Valid @RequestBody RegisterRequest registerRequest,
            BindingResult bindingResult) {

        // Handle validation errors
        ResponseEntity<?> errors = getBindingError(bindingResult);
        if (errors != null) return errors;
        
        // Check existing user
        if (userRepository.findByEmail(registerRequest.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(StandardResponse.error("이미 등록된 이메일입니다."));
        }

        try {
            // Create user
            User user = User.builder()
                    .name(registerRequest.getName())
                    .email(registerRequest.getEmail().toLowerCase())
                    .password(passwordEncoder.encode(registerRequest.getPassword()))
                    .build();

            user = userRepository.save(user);

            LoginResponse response = LoginResponse.builder()
                    .success(true)
                    .message("회원가입이 완료되었습니다.")
                    .user(new AuthUserDto(user.getId(), user.getName(), user.getEmail(), user.getProfileImage()))
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(response);

        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.error("Register error: ", e);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(StandardResponse.error("이미 등록된 이메일입니다."));
        } catch (IllegalArgumentException e) {
            log.error("Register error: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(StandardResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Register error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardResponse.error("회원가입 처리 중 오류가 발생했습니다."));
        }
    }
    
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인합니다. 성공 시 JWT 토큰이 반환됩니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "로그인 성공",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 입력값",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패 - 이메일 또는 비밀번호가 올바르지 않음",
            content = @Content(schema = @Schema(implementation = StandardResponse.class),
                examples = @ExampleObject(value = "{\"success\":false,\"message\":\"이메일 또는 비밀번호가 올바르지 않습니다.\"}"))),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = StandardResponse.class)))
    })
    @SecurityRequirement(name = "")
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest loginRequest,
            BindingResult bindingResult) {

        // Handle validation errors
        ResponseEntity<?> errors = getBindingError(bindingResult);
        if (errors != null) return errors;
        
        try {
            // Authenticate user
            User user = userRepository.findByEmail(loginRequest.getEmail().toLowerCase())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Generate JWT token
            String token = jwtService.generateToken(
                user.getEmail(),
                user.getId()
            );

            LoginResponse response = LoginResponse.builder()
                    .success(true)
                    .token(token)
                    .user(new AuthUserDto(user.getId(), user.getName(), user.getEmail(), user.getProfileImage()))
                    .build();

            return ResponseEntity.ok()
                    .header("Authorization", "Bearer " + token)
                    .body(response);

        } catch (UsernameNotFoundException | BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(StandardResponse.error("이메일 또는 비밀번호가 올바르지 않습니다."));
        } catch (Exception e) {
            log.error("Login error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardResponse.error("로그인 처리 중 오류가 발생했습니다."));
        }
    }
    
    @Operation(summary = "로그아웃", description = "클라이언트 토큰을 폐기하면 로그아웃이 완료됩니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "로그아웃 성공",
            content = @Content(schema = @Schema(implementation = StandardResponse.class),
                examples = @ExampleObject(value = "{\"success\":true,\"message\":\"로그아웃이 완료되었습니다.\"}"))),
        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = StandardResponse.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<StandardResponse<Void>> logout() {

        try {
            SecurityContextHolder.clearContext();
            
            return ResponseEntity.ok(StandardResponse.success("로그아웃이 완료되었습니다.", null));

        } catch (Exception e) {
            log.error("Logout error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardResponse.error("로그아웃 처리 중 오류가 발생했습니다."));
        }
    }
    

    @Operation(summary = "토큰 검증", description = "JWT 토큰의 유효성을 검증합니다. x-auth-token 또는 Authorization 헤더 중 하나를 사용하세요.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "토큰 검증 성공",
            content = @Content(schema = @Schema(implementation = TokenVerifyResponse.class))),
        @ApiResponse(responseCode = "400", description = "토큰 누락",
            content = @Content(schema = @Schema(implementation = TokenVerifyResponse.class),
                examples = @ExampleObject(value = "{\"valid\":false,\"message\":\"토큰이 필요합니다.\"}"))),
        @ApiResponse(responseCode = "401", description = "유효하지 않은 토큰",
            content = @Content(schema = @Schema(implementation = TokenVerifyResponse.class),
                examples = @ExampleObject(value = "{\"valid\":false,\"message\":\"유효하지 않은 토큰입니다.\"}"))),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = TokenVerifyResponse.class)))
    })
    @SecurityRequirement(name = "")
    @PostMapping("/verify-token")
    public ResponseEntity<?> verifyToken(HttpServletRequest request) {
        try {
            log.debug("verify-token request received from {}", request.getRemoteAddr());
            String token = extractToken(request);
            
            if (token == null) {
                log.warn("verify-token: Token not found in request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new TokenVerifyResponse(false, "토큰이 필요합니다.", null));
            }

            if (!jwtService.validateToken(token)) {
                log.warn("verify-token: Invalid token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenVerifyResponse(false, "유효하지 않은 토큰입니다.", null));
            }

            String userId = jwtService.extractUserId(token);
            log.debug("verify-token: Valid token for user {}", userId);
            
            Optional<User> userOpt;
            try {
                userOpt = userRepository.findById(userId);
            } catch (Exception dbException) {
                log.error("Database error during token verification for user {}: {}", userId, dbException.getMessage());
                // MongoDB 연결 실패 시에도 토큰 자체는 유효하므로, 사용자 정보 없이 성공 처리
                // 이렇게 하면 프론트엔드가 계속 동작할 수 있음
                return ResponseEntity.ok(new TokenVerifyResponse(true, "토큰이 유효합니다. (DB 연결 일시 중단)", null));
            }

            if (userOpt.isEmpty()) {
                log.warn("verify-token: User not found for userId {}", userId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenVerifyResponse(false, "사용자를 찾을 수 없습니다.", null));
            }

            User user = userOpt.get();
            AuthUserDto authUserDto = new AuthUserDto(user.getId(), user.getName(), user.getEmail(), user.getProfileImage());
            log.debug("verify-token: Token verified successfully for user {}", userId);
            return ResponseEntity.ok(new TokenVerifyResponse(true, "토큰이 유효합니다.", authUserDto));

        } catch (Exception e) {
            log.error("Token verification error: ", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new TokenVerifyResponse(false, "토큰 검증 중 오류가 발생했습니다.", null));
        }
    }
    
    @Operation(summary = "토큰 갱신", description = "만료된 토큰을 갱신합니다. 새로운 JWT가 반환됩니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "토큰 갱신 성공",
            content = @Content(schema = @Schema(implementation = TokenRefreshResponse.class))),
        @ApiResponse(responseCode = "400", description = "토큰 누락",
            content = @Content(schema = @Schema(implementation = TokenRefreshResponse.class),
                examples = @ExampleObject(value = "{\"success\":false,\"message\":\"토큰이 필요합니다.\"}"))),
        @ApiResponse(responseCode = "401", description = "유효하지 않은 사용자 또는 토큰",
            content = @Content(schema = @Schema(implementation = TokenRefreshResponse.class),
                examples = @ExampleObject(value = "{\"success\":false,\"message\":\"유효하지 않은 토큰입니다.\"}"))),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = TokenRefreshResponse.class)))
    })
    @SecurityRequirement(name = "")
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            
            if (token == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new TokenRefreshResponse(false, "토큰이 필요합니다.", null));
            }

            // 만료된 토큰이라도 사용자 정보는 추출 가능
            String userId = jwtService.extractUserIdFromExpiredToken(token);
            
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenRefreshResponse(false, "유효하지 않은 토큰입니다.", null));
            }
            
            Optional<User> userOpt;
            try {
                userOpt = userRepository.findById(userId);
            } catch (Exception dbException) {
                log.error("Database error during token refresh for user {}: {}", userId, dbException.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new TokenRefreshResponse(false, "데이터베이스 연결 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", null));
            }

            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenRefreshResponse(false, "사용자를 찾을 수 없습니다.", null));
            }

            User user = userOpt.get();
            String newToken = jwtService.generateToken(
                user.getEmail(),
                user.getId()
            );
            return ResponseEntity.ok(new TokenRefreshResponse(true, "토큰이 갱신되었습니다.", newToken));

        } catch (Exception e) {
            log.error("Token refresh error: ", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new TokenRefreshResponse(false, "토큰 갱신 중 오류가 발생했습니다.", null));
        }
    }
    
    private String extractToken(HttpServletRequest request) {
        String token = request.getHeader("x-auth-token");
        if (token != null && !token.isEmpty()) {
            return token;
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
    
    private ResponseEntity<?> getBindingError(BindingResult bindingResult) {
        if (!bindingResult.hasErrors()) {
            return null;
        }
        List<ValidationError> errors = bindingResult.getFieldErrors().stream()
                .map(error -> ValidationError.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .build())
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(StandardResponse.validationError("입력값이 올바르지 않습니다.", errors));
    }
}

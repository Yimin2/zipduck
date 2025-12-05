package com.zipduck.api.auth;

import com.zipduck.api.auth.dto.*;
import com.zipduck.domain.auth.AuthenticationService;
import com.zipduck.domain.auth.JwtTokenProvider;
import com.zipduck.domain.auth.RefreshToken;
import com.zipduck.domain.auth.RefreshTokenService;
import com.zipduck.domain.user.User;
import com.zipduck.domain.user.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 API Controller
 * 회원가입, 로그인, 로그아웃 엔드포인트
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final int REFRESH_TOKEN_MAX_AGE = 7 * 24 * 60 * 60; // 7일

    /**
     * 회원가입
     * POST /api/v1/auth/signup
     */
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletResponse response
    ) {
        log.info("Signup request for email: {}", request.getEmail());

        AuthenticationService.AuthResponse serviceResponse = authenticationService.signup(
                request.getEmail(),
                request.getPassword(),
                request.getUsername()
        );

        // Refresh Token을 HttpOnly 쿠키로 설정
        setRefreshTokenCookie(response, serviceResponse.getRefreshToken());

        // API 응답 (Access Token만 포함)
        AuthResponse apiResponse = AuthResponse.from(serviceResponse);

        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

    /**
     * 로그인
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        log.info("Login request for email: {}", request.getEmail());

        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthenticationService.AuthResponse serviceResponse = authenticationService.login(
                request.getEmail(),
                request.getPassword(),
                ipAddress,
                userAgent
        );

        // Refresh Token을 HttpOnly 쿠키로 설정
        setRefreshTokenCookie(response, serviceResponse.getRefreshToken());

        // API 응답 (Access Token만 포함)
        AuthResponse apiResponse = AuthResponse.from(serviceResponse);

        return ResponseEntity.ok(apiResponse);
    }

    /**
     * Refresh Token을 HttpOnly 쿠키로 설정
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // HTTPS에서만 전송
        cookie.setPath("/");
        cookie.setMaxAge(REFRESH_TOKEN_MAX_AGE);
        // cookie.setSameSite("Strict"); // Spring Boot 3.x에서는 별도 설정 필요
        response.addCookie(cookie);
    }

    /**
     * 토큰 갱신
     * POST /api/v1/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletResponse response
    ) {
        log.info("Token refresh request");

        try {
            // Refresh Token Rotation
            RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(request.getRefreshToken());

            // 새로운 Access Token 발급
            String newAccessToken = jwtTokenProvider.generateAccessToken(newRefreshToken.getUserId());

            // Refresh Token을 HttpOnly 쿠키로 설정
            setRefreshTokenCookie(response, newRefreshToken.getToken());

            // 응답
            TokenResponse tokenResponse = TokenResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken.getToken())
                    .tokenType("Bearer")
                    .expiresIn(3600L) // 1시간 (초 단위)
                    .build();

            return ResponseEntity.ok(tokenResponse);

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * 로그아웃
     * POST /api/v1/auth/logout
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletResponse response
    ) {
        log.info("Logout request");

        try {
            // Refresh Token 무효화
            refreshTokenService.revokeRefreshToken(request.getRefreshToken());

            // 쿠키 삭제
            Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, null);
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setPath("/");
            cookie.setMaxAge(0); // 즉시 삭제
            response.addCookie(cookie);

            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.warn("Logout failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 현재 로그인한 사용자 정보 조회
     * GET /api/v1/auth/me
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        log.info("Get current user request");

        try {
            // Spring Security의 Authentication에서 userId 추출
            Long userId = Long.parseLong(authentication.getName());

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            UserResponse userResponse = UserResponse.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .username(user.getUsername())
                    .provider(user.getProvider().name())
                    .status(user.getStatus().name())
                    .createdAt(user.getCreatedAt())
                    .build();

            return ResponseEntity.ok(userResponse);

        } catch (Exception e) {
            log.error("Failed to get current user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 클라이언트 IP 추출
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}

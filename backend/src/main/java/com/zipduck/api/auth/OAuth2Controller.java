package com.zipduck.api.auth;

import com.zipduck.api.auth.dto.AuthResponse;
import com.zipduck.api.auth.dto.LinkAccountRequest;
import com.zipduck.api.auth.dto.LinkRequiredResponse;
import com.zipduck.domain.auth.OAuth2Service;
import com.zipduck.domain.user.AuthProvider;
import com.zipduck.infrastructure.oauth2.GoogleOAuth2UserInfo;
import com.zipduck.infrastructure.oauth2.KakaoOAuth2UserInfo;
import com.zipduck.infrastructure.oauth2.OAuth2UserInfo;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OAuth2 소셜 로그인 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/oauth2")
@RequiredArgsConstructor
public class OAuth2Controller {

    private final OAuth2Service oauth2Service;

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final int REFRESH_TOKEN_MAX_AGE = 7 * 24 * 60 * 60; // 7일

    /**
     * OAuth2 인증 콜백
     * GET /api/v1/oauth2/callback/{provider}
     *
     * Spring Security OAuth2가 자동으로 처리하며, 이 메서드는 수동 처리용
     * 실제 OAuth2 인증은 Spring Security OAuth2 Client가 자동으로 처리합니다.
     * 이 엔드포인트는 참고용이며, 실제로는 SecurityConfig에서 successHandler를 통해 처리됩니다.
     */
    @GetMapping("/callback/{provider}")
    public ResponseEntity<?> oauthCallback(
            @PathVariable String provider,
            @RequestParam(required = false) Map<String, Object> params,
            HttpServletResponse response
    ) {
        log.info("OAuth2 callback: provider={}", provider);

        try {
            // 실제 OAuth2 사용자 정보는 Spring Security Context에서 가져옵니다
            // 이 방법은 간소화된 구현이며, 실제로는 OAuth2UserService를 구현해야 합니다
            if (params == null || params.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No OAuth2 user information provided"));
            }

            Map<String, Object> attributes = params;

            // Provider별 사용자 정보 파싱
            AuthProvider authProvider = AuthProvider.valueOf(provider.toUpperCase());
            OAuth2UserInfo userInfo = createOAuth2UserInfo(authProvider, attributes);

            // OAuth2 사용자 처리
            Object result = oauth2Service.processOAuthUser(authProvider, userInfo);

            // LinkRequiredResponse인 경우
            if (result instanceof LinkRequiredResponse) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
            }

            // AuthResponse인 경우
            AuthResponse authResponse = (AuthResponse) result;

            // Refresh Token을 HttpOnly 쿠키로 설정
            setRefreshTokenCookie(response, authResponse.getRefreshToken());

            return ResponseEntity.ok(authResponse);

        } catch (Exception e) {
            log.error("OAuth2 callback failed: provider={}, error={}", provider, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "OAuth2 authentication failed"));
        }
    }

    /**
     * 계정 연동 처리
     * POST /api/v1/oauth2/link
     */
    @PostMapping("/link")
    public ResponseEntity<?> linkAccount(
            @Valid @RequestBody LinkAccountRequest request,
            HttpServletResponse response
    ) {
        log.info("Account linking request: linkToken={}, confirm={}", request.getLinkToken(), request.getConfirm());

        try {
            AuthResponse authResponse = oauth2Service.linkOAuthAccount(request.getLinkToken(), request.getConfirm());

            if (authResponse == null) {
                // 연동 거부
                return ResponseEntity.ok(Map.of("message", "Account linking rejected"));
            }

            // 연동 승인 - Refresh Token 쿠키 설정
            setRefreshTokenCookie(response, authResponse.getRefreshToken());

            return ResponseEntity.ok(authResponse);

        } catch (IllegalArgumentException e) {
            log.warn("Account linking failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Account linking failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Account linking failed"));
        }
    }

    /**
     * Provider별 OAuth2UserInfo 생성
     */
    private OAuth2UserInfo createOAuth2UserInfo(AuthProvider provider, Map<String, Object> attributes) {
        switch (provider) {
            case GOOGLE:
                return new GoogleOAuth2UserInfo(attributes);
            case KAKAO:
                return new KakaoOAuth2UserInfo(attributes);
            default:
                throw new IllegalArgumentException("Unsupported provider: " + provider);
        }
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
        response.addCookie(cookie);
    }
}

package com.zipduck.domain.auth;

import com.zipduck.api.auth.dto.AuthResponse;
import com.zipduck.api.auth.dto.LinkRequiredResponse;
import com.zipduck.domain.user.AuthProvider;
import com.zipduck.domain.user.User;
import com.zipduck.domain.user.UserRepository;
import com.zipduck.infrastructure.oauth2.OAuth2UserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * OAuth2 소셜 로그인 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2Service {

    private final UserRepository userRepository;
    private final LinkTokenRepository linkTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    /**
     * OAuth2 사용자 정보 처리
     * - 신규 사용자: 회원가입 후 토큰 발급
     * - 기존 소셜 계정: 로그인 처리
     * - 기존 LOCAL 계정: 연동 확인 요청
     *
     * @param provider OAuth2 제공자 (GOOGLE, KAKAO)
     * @param oauthUserInfo OAuth2 사용자 정보
     * @return AuthResponse 또는 LinkRequiredResponse (Object로 반환)
     */
    @Transactional
    public Object processOAuthUser(AuthProvider provider, OAuth2UserInfo oauthUserInfo) {
        String email = oauthUserInfo.getEmail();
        String providerId = oauthUserInfo.getProviderId();

        // 1. 이미 같은 Provider로 가입한 사용자인지 확인
        User existingUser = userRepository.findByEmailAndProvider(email, provider).orElse(null);

        if (existingUser != null) {
            // 기존 소셜 계정 로그인
            log.info("Existing OAuth2 user login: email={}, provider={}", email, provider);
            return generateAuthResponse(existingUser);
        }

        // 2. LOCAL 계정이 존재하는지 확인
        User localUser = userRepository.findByEmailAndProvider(email, AuthProvider.LOCAL).orElse(null);

        if (localUser != null) {
            // 계정 연동 필요
            log.info("Account linking required: email={}, provider={}", email, provider);
            return createLinkRequiredResponse(email, provider, providerId);
        }

        // 3. 신규 사용자 - 회원가입
        log.info("New OAuth2 user signup: email={}, provider={}", email, provider);
        User newUser = User.builder()
                .email(email)
                .username(oauthUserInfo.getName())
                .provider(provider)
                .providerId(providerId)
                .password(null) // 소셜 로그인은 비밀번호 없음
                .status(User.UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(newUser);
        return generateAuthResponse(savedUser);
    }

    /**
     * 계정 연동 처리
     *
     * @param linkToken 임시 연동 토큰
     * @param confirm true: 연동 승인, false: 거부
     * @return AuthResponse (연동 승인 시) 또는 null (거부 시)
     */
    @Transactional
    public AuthResponse linkOAuthAccount(String linkToken, Boolean confirm) {
        LinkToken token = linkTokenRepository.findById(linkToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired link token"));

        if (!token.isValid()) {
            throw new IllegalArgumentException("Link token has expired");
        }

        if (confirm == null || !confirm) {
            // 연동 거부
            linkTokenRepository.deleteById(linkToken);
            log.info("Account linking rejected: email={}", token.getEmail());
            return null;
        }

        // 연동 승인 - LOCAL 계정에 provider 정보 추가
        User localUser = userRepository.findByEmailAndProvider(token.getEmail(), AuthProvider.LOCAL)
                .orElseThrow(() -> new IllegalArgumentException("Local account not found"));

        // 소셜 계정으로 새 User 엔티티 생성 (동일 이메일, 다른 provider)
        AuthProvider provider = AuthProvider.valueOf(token.getProvider());
        User socialUser = User.builder()
                .email(token.getEmail())
                .username(localUser.getUsername())
                .provider(provider)
                .providerId(token.getProviderId())
                .password(null)
                .status(User.UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(socialUser);
        linkTokenRepository.deleteById(linkToken);

        log.info("Account linked successfully: email={}, provider={}", token.getEmail(), provider);
        return generateAuthResponse(savedUser);
    }

    /**
     * 계정 연동 필요 응답 생성
     */
    private LinkRequiredResponse createLinkRequiredResponse(String email, AuthProvider provider, String providerId) {
        String tokenValue = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(5);

        LinkToken linkToken = LinkToken.builder()
                .token(tokenValue)
                .email(email)
                .provider(provider.name())
                .providerId(providerId)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();

        linkTokenRepository.save(linkToken);

        return LinkRequiredResponse.builder()
                .linkToken(tokenValue)
                .email(email)
                .provider(provider.name())
                .message("An account with this email already exists. Would you like to link it?")
                .build();
    }

    /**
     * AuthResponse 생성 (Access Token + Refresh Token)
     */
    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        RefreshToken refreshToken = refreshTokenService.issueRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(3600) // Integer로 변경
                .refreshToken(refreshToken.getToken())
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .username(user.getUsername())
                        .provider(user.getProvider().name())
                        .status(user.getStatus().name())
                        .build())
                .build();
    }
}

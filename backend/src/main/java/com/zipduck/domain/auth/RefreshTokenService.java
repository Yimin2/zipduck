package com.zipduck.domain.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Refresh Token 관리 서비스
 * - Token 발급, Rotation, 무효화 담당
 * - Redis 기반 저장 및 TTL 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * 새로운 Refresh Token 발급
     *
     * @param userId 사용자 ID
     * @return 발급된 RefreshToken
     */
    public RefreshToken issueRefreshToken(Long userId) {
        String tokenValue = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(7); // 7일 만료

        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenValue)
                .userId(userId)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();

        refreshTokenRepository.save(refreshToken);
        log.info("Refresh token issued for user: {}", userId);

        return refreshToken;
    }

    /**
     * Refresh Token Rotation
     * - 기존 토큰 무효화 후 새 토큰 발급
     * - 재사용 감지를 위해 사용
     *
     * @param oldTokenValue 기존 토큰 값
     * @return 새로 발급된 RefreshToken
     * @throws IllegalArgumentException 유효하지 않은 토큰
     */
    public RefreshToken rotateRefreshToken(String oldTokenValue) {
        RefreshToken oldToken = refreshTokenRepository.findById(oldTokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired refresh token"));

        if (!oldToken.isValid()) {
            // 만료된 토큰으로 재사용 시도 - 보안 위협
            log.warn("Expired token reuse detected for user: {}", oldToken.getUserId());
            revokeAllUserTokens(oldToken.getUserId());
            throw new IllegalStateException("Token reuse detected - all tokens revoked");
        }

        // 기존 토큰 무효화
        refreshTokenRepository.deleteById(oldTokenValue);
        log.info("Old refresh token rotated for user: {}", oldToken.getUserId());

        // 새 토큰 발급
        return issueRefreshToken(oldToken.getUserId());
    }

    /**
     * 특정 Refresh Token 무효화
     *
     * @param tokenValue 무효화할 토큰 값
     */
    public void revokeRefreshToken(String tokenValue) {
        refreshTokenRepository.findById(tokenValue).ifPresent(token -> {
            refreshTokenRepository.deleteById(tokenValue);
            log.info("Refresh token revoked for user: {}", token.getUserId());
        });
    }

    /**
     * 사용자의 모든 Refresh Token 무효화
     * - 전체 기기 로그아웃 시 사용
     * - 보안 위협 감지 시 사용
     *
     * @param userId 사용자 ID
     */
    public void revokeAllUserTokens(Long userId) {
        Iterable<RefreshToken> userTokens = refreshTokenRepository.findByUserId(userId);
        refreshTokenRepository.deleteAll(userTokens);
        log.warn("All refresh tokens revoked for user: {}", userId);
    }

    /**
     * Refresh Token 검증
     *
     * @param tokenValue 토큰 값
     * @return 유효한 RefreshToken
     * @throws IllegalArgumentException 유효하지 않은 토큰
     */
    public RefreshToken validateRefreshToken(String tokenValue) {
        RefreshToken token = refreshTokenRepository.findById(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (!token.isValid()) {
            throw new IllegalArgumentException("Expired refresh token");
        }

        return token;
    }
}

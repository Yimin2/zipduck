package com.zipduck.domain.auth;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.time.LocalDateTime;

/**
 * Refresh Token Redis Value Object
 * TTL: 7일 (604800초)
 */
@RedisHash(value = "refresh_token", timeToLive = 604800)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    private String token;  // UUID v4

    @Indexed
    private Long userId;

    private LocalDateTime issuedAt;

    private LocalDateTime expiresAt;

    /**
     * 토큰이 유효한지 확인
     */
    public boolean isValid() {
        return LocalDateTime.now().isBefore(expiresAt);
    }

    /**
     * 새로운 Refresh Token 생성
     */
    public static RefreshToken create(String token, Long userId, int expirationSeconds) {
        LocalDateTime now = LocalDateTime.now();
        return RefreshToken.builder()
                .token(token)
                .userId(userId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .build();
    }
}

package com.zipduck.domain.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.time.LocalDateTime;

/**
 * 계정 연동용 임시 토큰 (Redis 저장)
 * TTL: 5분
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "link_token", timeToLive = 300) // 5분 = 300초
public class LinkToken {

    @Id
    private String token; // UUID

    private String email;
    private String provider;
    private String providerId;

    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;

    public boolean isValid() {
        return LocalDateTime.now().isBefore(expiresAt);
    }
}

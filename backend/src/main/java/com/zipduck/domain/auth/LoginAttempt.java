package com.zipduck.domain.auth;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.time.LocalDateTime;

/**
 * 로그인 시도 추적 Redis Value Object
 * TTL: 30분 (1800초)
 * 5회 실패 시 계정 잠금
 */
@RedisHash(value = "login_attempt", timeToLive = 1800)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LoginAttempt {

    @Id
    private String email;

    @Builder.Default
    private Integer count = 0;

    private LocalDateTime lastAttemptAt;

    /**
     * 로그인 시도 횟수 증가
     */
    public void increment() {
        this.count++;
        this.lastAttemptAt = LocalDateTime.now();
    }

    /**
     * 계정 잠금 여부 확인
     */
    public boolean isLocked() {
        return count >= 5;
    }

    /**
     * 로그인 시도 초기화
     */
    public void reset() {
        this.count = 0;
        this.lastAttemptAt = null;
    }

    /**
     * 새로운 로그인 시도 생성
     */
    public static LoginAttempt createNew(String email) {
        return LoginAttempt.builder()
                .email(email)
                .count(1)
                .lastAttemptAt(LocalDateTime.now())
                .build();
    }
}

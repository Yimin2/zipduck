package com.zipduck.domain.user;

import com.zipduck.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 로그인 이력 엔티티
 * 보안 감사 및 이상 로그인 탐지를 위한 로그인 기록
 */
@Entity
@Table(name = "login_histories",
       indexes = @Index(name = "idx_user_id_login_at", columnList = "user_id,login_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LoginHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "login_at", nullable = false)
    private LocalDateTime loginAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(nullable = false)
    private Boolean success;

    @Column(name = "ip_address", length = 45)  // IPv6 최대 길이
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;  // 실패 시 이유

    /**
     * 로그인 성공 기록 생성
     */
    public static LoginHistory success(User user, AuthProvider provider, String ipAddress, String userAgent) {
        return LoginHistory.builder()
                .user(user)
                .loginAt(LocalDateTime.now())
                .provider(provider)
                .success(true)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
    }

    /**
     * 로그인 실패 기록 생성
     */
    public static LoginHistory failure(User user, AuthProvider provider, String ipAddress, String userAgent, String reason) {
        return LoginHistory.builder()
                .user(user)
                .loginAt(LocalDateTime.now())
                .provider(provider)
                .success(false)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .failureReason(reason)
                .build();
    }
}

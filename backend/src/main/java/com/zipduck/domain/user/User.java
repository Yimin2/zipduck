package com.zipduck.domain.user;

import com.zipduck.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * User entity representing a ZipDuck user
 * Simple authentication entity - detailed profile stored in UserProfile
 * Supports both local (email/password) and social login (Google, Kakao)
 */
@Entity
@Table(name = "users",
       uniqueConstraints = @UniqueConstraint(columnNames = {"email", "provider"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(nullable = true)  // MODIFIED: Nullable for social login
    private String password; // BCrypt encoded (null for social login)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;  // NEW: OAuth provider

    @Column(name = "provider_id")
    private String providerId;  // NEW: Provider's user ID (for social login)

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private UserProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    public enum UserStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED
    }

    /**
     * 소셜 로그인 계정인지 확인
     */
    public boolean isSocialLogin() {
        return provider != AuthProvider.LOCAL;
    }

    /**
     * 비밀번호 로그인 가능 여부 확인
     */
    public boolean canLoginWithPassword() {
        return provider == AuthProvider.LOCAL && password != null;
    }

    public void updateProfile(UserProfile profile) {
        this.profile = profile;
        profile.setUser(this);
    }

    /**
     * 소셜 로그인 계정 연동
     */
    public void linkSocialAccount(AuthProvider provider, String providerId) {
        if (this.provider == AuthProvider.LOCAL && this.password != null) {
            // 기존 LOCAL 계정에 소셜 로그인 연동 허용
            this.provider = provider;
            this.providerId = providerId;
        }
    }
}

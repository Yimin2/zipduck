package com.zipduck.domain.notification;

import com.zipduck.domain.BaseEntity;
import com.zipduck.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * 알림 설정 엔티티
 * 사용자별 알림 수신 방법 및 타입 설정
 */
@Entity
@Table(name = "notification_settings",
       uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NotificationSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private Boolean emailEnabled = true;  // 이메일 알림 활성화

    @Column(name = "push_enabled", nullable = false)
    @Builder.Default
    private Boolean pushEnabled = false;  // 푸시 알림 활성화 (향후용)

    @Column(name = "new_subscription_enabled", nullable = false)
    @Builder.Default
    private Boolean newSubscriptionEnabled = true;  // 신규 청약 알림

    @Column(name = "expiring_subscription_enabled", nullable = false)
    @Builder.Default
    private Boolean expiringSubscriptionEnabled = true;  // 마감 임박 알림

    /**
     * 사용자가 알림을 받을지 여부 결정
     */
    public boolean shouldSendNotification(NotificationType type, NotificationMethod method) {
        // 메소드별 활성화 확인
        if (method == NotificationMethod.EMAIL && !emailEnabled) {
            return false;
        }
        if (method == NotificationMethod.PUSH && !pushEnabled) {
            return false;
        }

        // 타입별 활성화 확인
        if (type == NotificationType.NEW_SUBSCRIPTION && !newSubscriptionEnabled) {
            return false;
        }
        if (type == NotificationType.EXPIRING_SUBSCRIPTION && !expiringSubscriptionEnabled) {
            return false;
        }

        return true;
    }

    /**
     * 알림 설정 업데이트
     */
    public void updateSettings(Boolean emailEnabled, Boolean pushEnabled,
                               Boolean newSubscriptionEnabled, Boolean expiringSubscriptionEnabled) {
        if (emailEnabled != null) {
            this.emailEnabled = emailEnabled;
        }
        if (pushEnabled != null) {
            this.pushEnabled = pushEnabled;
        }
        if (newSubscriptionEnabled != null) {
            this.newSubscriptionEnabled = newSubscriptionEnabled;
        }
        if (expiringSubscriptionEnabled != null) {
            this.expiringSubscriptionEnabled = expiringSubscriptionEnabled;
        }
    }

    /**
     * 기본 알림 설정 생성
     */
    public static NotificationSetting createDefault(User user) {
        return NotificationSetting.builder()
                .user(user)
                .emailEnabled(true)
                .pushEnabled(false)
                .newSubscriptionEnabled(true)
                .expiringSubscriptionEnabled(true)
                .build();
    }
}

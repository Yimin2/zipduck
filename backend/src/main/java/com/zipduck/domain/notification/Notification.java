package com.zipduck.domain.notification;

import com.zipduck.domain.BaseEntity;
import com.zipduck.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 알림 엔티티
 * 사용자에게 발송되는 알림 정보 저장
 */
@Entity
@Table(name = "notifications",
       indexes = {
           @Index(name = "idx_user_id_created_at", columnList = "user_id,created_at"),
           @Index(name = "idx_status_created_at", columnList = "status,created_at")
       })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "subscription_id")
    private Long subscriptionId;  // Subscription과 느슨한 결합

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;  // 발송 실패 시 이유

    /**
     * 알림 발송 성공 처리
     */
    public void markAsSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.failureReason = null;
    }

    /**
     * 알림 발송 실패 처리
     */
    public void markAsFailed(String reason) {
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason;
    }

    /**
     * 재시도 가능 여부
     */
    public boolean canRetry() {
        return this.status == NotificationStatus.FAILED;
    }

    /**
     * 재시도 준비
     */
    public void prepareForRetry() {
        if (canRetry()) {
            this.status = NotificationStatus.PENDING;
            this.failureReason = null;
        }
    }
}

package com.zipduck.domain.notification;

/**
 * 알림 발송 상태
 */
public enum NotificationStatus {
    /**
     * 발송 대기 중
     */
    PENDING,

    /**
     * 발송 완료
     */
    SENT,

    /**
     * 발송 실패
     */
    FAILED
}

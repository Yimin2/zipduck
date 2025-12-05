package com.zipduck.domain.notification;

/**
 * 알림 발송 방법
 */
public enum NotificationMethod {
    /**
     * 이메일 알림
     */
    EMAIL,

    /**
     * 푸시 알림 (향후 확장용)
     */
    PUSH
}

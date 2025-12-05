package com.zipduck.domain.notification;

/**
 * 알림 타입
 */
public enum NotificationType {
    /**
     * 신규 청약 등록 알림
     */
    NEW_SUBSCRIPTION,

    /**
     * 청약 마감 임박 알림 (24시간 이내)
     */
    EXPIRING_SUBSCRIPTION
}

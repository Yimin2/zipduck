package com.zipduck.application.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 청약 마감 임박 Domain Event
 */
@Getter
public class SubscriptionExpiringEvent {

    private final Long subscriptionId;
    private final LocalDateTime expiresAt;
    private final LocalDateTime occurredAt;

    public SubscriptionExpiringEvent(Long subscriptionId, LocalDateTime expiresAt) {
        this.subscriptionId = subscriptionId;
        this.expiresAt = expiresAt;
        this.occurredAt = LocalDateTime.now();
    }
}

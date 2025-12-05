package com.zipduck.application.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 청약 생성 Domain Event
 */
@Getter
public class SubscriptionCreatedEvent {

    private final Long subscriptionId;
    private final LocalDateTime occurredAt;

    public SubscriptionCreatedEvent(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
        this.occurredAt = LocalDateTime.now();
    }
}

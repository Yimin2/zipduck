package com.zipduck.domain.notification;

import com.zipduck.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Notification Repository
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 사용자별 알림 조회 (최근순)
     */
    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    /**
     * 사용자별 상태별 알림 조회
     */
    Page<Notification> findByUserAndStatusOrderByCreatedAtDesc(User user, NotificationStatus status, Pageable pageable);

    /**
     * 재시도 대상 알림 조회 (FAILED 상태, 생성 시간 기준)
     */
    @Query("SELECT n FROM Notification n WHERE n.status = 'FAILED' AND n.createdAt > :since ORDER BY n.createdAt")
    List<Notification> findFailedNotificationsSince(@Param("since") LocalDateTime since);

    /**
     * 특정 청약에 대한 중복 알림 확인
     */
    boolean existsByUserAndSubscriptionIdAndTypeAndStatus(
            User user, Long subscriptionId, NotificationType type, NotificationStatus status
    );

    /**
     * 발송 대기 중인 알림 조회
     */
    List<Notification> findByStatusOrderByCreatedAt(NotificationStatus status);
}

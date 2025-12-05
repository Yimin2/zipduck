package com.zipduck.domain.notification;

import com.zipduck.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * NotificationSetting Repository
 */
@Repository
public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    /**
     * 사용자의 알림 설정 조회
     */
    Optional<NotificationSetting> findByUser(User user);

    /**
     * 사용자의 알림 설정 존재 여부
     */
    boolean existsByUser(User user);
}

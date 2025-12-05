package com.zipduck.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LoginHistory Repository
 */
@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    /**
     * 사용자별 로그인 이력 조회 (최근순)
     */
    Page<LoginHistory> findByUserOrderByLoginAtDesc(User user, Pageable pageable);

    /**
     * 사용자의 최근 로그인 이력 조회
     */
    List<LoginHistory> findTop10ByUserOrderByLoginAtDesc(User user);

    /**
     * 특정 기간 내 로그인 실패 횟수 조회
     */
    long countByUserAndSuccessFalseAndLoginAtAfter(User user, LocalDateTime after);

    /**
     * 특정 IP에서의 로그인 실패 횟수 조회
     */
    long countByIpAddressAndSuccessFalseAndLoginAtAfter(String ipAddress, LocalDateTime after);
}

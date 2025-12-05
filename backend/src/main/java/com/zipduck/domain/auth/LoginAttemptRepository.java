package com.zipduck.domain.auth;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * LoginAttempt Redis Repository
 */
@Repository
public interface LoginAttemptRepository extends CrudRepository<LoginAttempt, String> {

    /**
     * 이메일로 로그인 시도 조회
     */
    Optional<LoginAttempt> findById(String email);

    /**
     * 이메일로 로그인 시도 삭제
     */
    void deleteById(String email);
}

package com.zipduck.domain.auth;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * RefreshToken Redis Repository
 */
@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {

    /**
     * 사용자 ID로 모든 Refresh Token 조회
     */
    List<RefreshToken> findByUserId(Long userId);

    /**
     * 사용자 ID로 Refresh Token 삭제
     */
    void deleteByUserId(Long userId);

    /**
     * 토큰 값으로 조회
     */
    Optional<RefreshToken> findById(String token);
}

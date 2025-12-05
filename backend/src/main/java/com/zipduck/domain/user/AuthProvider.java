package com.zipduck.domain.user;

/**
 * OAuth 2.0 Provider 타입
 * 사용자 계정의 인증 제공자를 구분
 */
public enum AuthProvider {
    /**
     * 이메일/비밀번호 기반 로컬 계정
     */
    LOCAL,

    /**
     * Google OAuth 2.0 소셜 로그인
     */
    GOOGLE,

    /**
     * Kakao OAuth 2.0 소셜 로그인
     */
    KAKAO
}

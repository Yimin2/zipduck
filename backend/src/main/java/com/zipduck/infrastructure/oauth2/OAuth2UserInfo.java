package com.zipduck.infrastructure.oauth2;

/**
 * OAuth2 제공자의 사용자 정보 추상화 인터페이스
 */
public interface OAuth2UserInfo {

    /**
     * OAuth2 제공자의 사용자 ID
     */
    String getProviderId();

    /**
     * 사용자 이메일
     */
    String getEmail();

    /**
     * 사용자 이름
     */
    String getName();
}

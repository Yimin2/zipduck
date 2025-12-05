package com.zipduck.api.auth.dto;

import com.zipduck.domain.user.AuthProvider;
import com.zipduck.domain.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 인증 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String tokenType;
    private Integer expiresIn;
    private String refreshToken; // Refresh Token 추가
    private UserInfo user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String email;
        private String username;
        private String provider; // String으로 변경
        private String status; // String으로 변경
    }

    /**
     * Service AuthResponse를 API AuthResponse로 변환
     */
    public static AuthResponse from(com.zipduck.domain.auth.AuthenticationService.AuthResponse serviceResponse) {
        return AuthResponse.builder()
                .accessToken(serviceResponse.getAccessToken())
                .tokenType(serviceResponse.getTokenType())
                .expiresIn(serviceResponse.getExpiresIn())
                .refreshToken(serviceResponse.getRefreshToken())
                .user(UserInfo.builder()
                        .id(serviceResponse.getUserId())
                        .email(serviceResponse.getEmail())
                        .username(serviceResponse.getUsername())
                        .provider(serviceResponse.getProvider().name())
                        .status(serviceResponse.getStatus().name())
                        .build())
                .build();
    }
}

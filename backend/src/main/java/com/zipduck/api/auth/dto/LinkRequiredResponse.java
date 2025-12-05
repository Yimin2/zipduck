package com.zipduck.api.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계정 연동 필요 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkRequiredResponse {

    private String linkToken;
    private String email;
    private String provider;
    private String message;
}

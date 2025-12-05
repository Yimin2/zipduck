package com.zipduck.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계정 연동 요청 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LinkAccountRequest {

    @NotBlank(message = "Link token is required")
    private String linkToken;

    private Boolean confirm; // true: 연동 승인, false: 연동 거부
}

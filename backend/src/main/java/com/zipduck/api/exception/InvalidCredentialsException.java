package com.zipduck.api.exception;

/**
 * 잘못된 인증 정보 예외
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}

package com.zipduck.api.exception;

/**
 * 계정 잠금 예외
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException(String message) {
        super(message);
    }
}

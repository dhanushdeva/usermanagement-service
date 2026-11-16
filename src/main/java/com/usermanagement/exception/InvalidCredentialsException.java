package com.usermanagement.exception;

import lombok.Getter;

@Getter
public class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String errorCode;

    public InvalidCredentialsException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}

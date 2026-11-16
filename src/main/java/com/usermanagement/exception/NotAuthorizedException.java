package com.usermanagement.exception;

import lombok.Getter;

@Getter
public class NotAuthorizedException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String errorCode;

    public NotAuthorizedException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}

package com.usermanagement.exception;

import lombok.Getter;

@Getter
public class UnhandledException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String errorCode;

    public UnhandledException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}

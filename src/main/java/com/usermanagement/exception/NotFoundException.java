package com.usermanagement.exception;

import lombok.Getter;

@Getter
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String entity;
    private final String errorCode;

    public NotFoundException(String message, String errorCode, String entity) {
        super(message);
        this.errorCode = errorCode;
        this.entity = entity;
    }
}

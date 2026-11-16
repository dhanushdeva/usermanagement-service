package com.usermanagement.exception;

import lombok.Getter;

@Getter
public class MissingRequestParamException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String errorCode;
    private final String entity;

    public MissingRequestParamException(String errorCode, String message, String entity) {
        super(message);
        this.errorCode = errorCode;
        this.entity = entity;
    }
}

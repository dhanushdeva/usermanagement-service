package com.usermanagement.exception;

import lombok.Getter;

/**
 * Single business exception of the service; the {@link ErrorCode} decides HTTP status and message.
 */
@Getter
public class UserManagementException extends RuntimeException {

    private final ErrorCode errorCode;
    /** The user id, role name, field name... the error is about (may be empty). */
    private final String entity;

    public UserManagementException(ErrorCode errorCode, String entity) {
        this(errorCode, entity, null);
    }

    public UserManagementException(ErrorCode errorCode, String entity, Throwable cause) {
        super(errorCode.name() + (entity == null || entity.isEmpty() ? "" : ": " + entity), cause);
        this.errorCode = errorCode;
        this.entity = entity == null ? "" : entity;
    }
}

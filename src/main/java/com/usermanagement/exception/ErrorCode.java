package com.usermanagement.exception;

import org.springframework.http.HttpStatus;

/**
 * Every error the service returns. The human-readable text lives in messages/messages.properties (key = code).
 */
public enum ErrorCode {
    KEYCLOAK_UNREACHABLE("UM001", HttpStatus.SERVICE_UNAVAILABLE),
    KEYCLOAK_BAD_REQUEST("UM002", HttpStatus.BAD_REQUEST),
    KEYCLOAK_ACCESS_DENIED("UM003", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_NOT_FOUND("UM004", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS("UM005", HttpStatus.CONFLICT),
    ROLE_ALREADY_EXISTS("UM006", HttpStatus.CONFLICT),
    ROLE_NOT_FOUND("UM007", HttpStatus.NOT_FOUND),
    REALM_NOT_FOUND("UM008", HttpStatus.NOT_FOUND),
    INVALID_CLIENT_CREDENTIALS("UM009", HttpStatus.UNAUTHORIZED),
    INVALID_REQUEST("UM100", HttpStatus.BAD_REQUEST),
    MISSING_ROLE_NAME("UM105", HttpStatus.BAD_REQUEST),
    MISSING_USER_DETAILS("UM107", HttpStatus.BAD_REQUEST),
    MISSING_USER_IDS("UM108", HttpStatus.BAD_REQUEST),
    MISSING_ROLE_DETAILS("UM109", HttpStatus.BAD_REQUEST),
    MISSING_USER_ID("UM110", HttpStatus.BAD_REQUEST),
    INVALID_USER_STATUS("UM111", HttpStatus.BAD_REQUEST),
    ACCESS_FORBIDDEN("UM112", HttpStatus.FORBIDDEN),
    EMAIL_VERIFICATION_DISABLED("UM113", HttpStatus.BAD_REQUEST),
    UNEXPECTED_ERROR("UM999", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final HttpStatus httpStatus;

    ErrorCode(String code, HttpStatus httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}

package com.usermanagement.exception;

import lombok.Getter;

/**
 * The service's own admin client could not be used against Keycloak (wrong secret or missing realm-management roles).
 * This is a configuration problem of the service, not a problem with the caller's token or payload.
 */
@Getter
public class KeycloakAdminClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String errorCode;
    private final String entity;

    public KeycloakAdminClientException(String message, String errorCode, String entity) {
        super(message);
        this.errorCode = errorCode;
        this.entity = entity;
    }
}

package com.usermanagement.identityprovider.keycloak;

import org.springframework.web.client.RestClientResponseException;

import com.usermanagement.exception.ErrorCode;
import com.usermanagement.exception.UserManagementException;

import lombok.extern.slf4j.Slf4j;

/**
 * Maps HTTP errors returned by Keycloak to the service's {@link ErrorCode}s.
 */
@Slf4j
final class KeycloakErrorTranslator {

    /** What the failed Keycloak call was about; decides which "not found"/"already exists" code is used. */
    enum KeycloakResource { REALM, USER, ROLE }

    private KeycloakErrorTranslator() {
    }

    static UserManagementException translate(RestClientResponseException ex, KeycloakResource resource, String entity) {
        log.warn("Keycloak returned HTTP {} for {} '{}': {}", ex.getStatusCode().value(), resource, entity,
                ex.getResponseBodyAsString());

        ErrorCode errorCode = switch (ex.getStatusCode().value()) {
            case 400 -> ErrorCode.KEYCLOAK_BAD_REQUEST;
            case 401 -> ErrorCode.INVALID_CLIENT_CREDENTIALS;
            case 403 -> ErrorCode.KEYCLOAK_ACCESS_DENIED;
            case 404 -> switch (resource) {
                case USER -> ErrorCode.USER_NOT_FOUND;
                case ROLE -> ErrorCode.ROLE_NOT_FOUND;
                case REALM -> ErrorCode.REALM_NOT_FOUND;
            };
            case 409 -> resource == KeycloakResource.ROLE ? ErrorCode.ROLE_ALREADY_EXISTS : ErrorCode.USER_ALREADY_EXISTS;
            default -> ErrorCode.UNEXPECTED_ERROR;
        };
        return new UserManagementException(errorCode, entity, ex);
    }
}

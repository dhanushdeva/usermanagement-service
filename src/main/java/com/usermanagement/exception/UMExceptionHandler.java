package com.usermanagement.exception;

import java.time.OffsetDateTime;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.usermanagement.util.MessageUtil;

/**
 * Converts exceptions to the error body. Same HTTP status per exception as bm-usermanagement-v4; the message is
 * always read from messages.properties by error code.
 */
@RestControllerAdvice
public class UMExceptionHandler {

    private static final String ACCESS_FORBIDDEN = "UM112";

    private final MessageUtil messageUtil;

    public UMExceptionHandler(MessageUtil messageUtil) {
        this.messageUtil = messageUtil;
    }

    @ExceptionHandler(value = {ConnectionTimeOutException.class})
    ResponseEntity<ErrorResponse> handleConnectionTimeoutException(ConnectionTimeOutException ex) {
        return getErrorResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR, ex.getErrorCode(), "");
    }

    @ExceptionHandler(value = {InvalidConfigException.class})
    ResponseEntity<ErrorResponse> handleInvalidConfigException(InvalidConfigException ex) {
        return getErrorResponseEntity(HttpStatus.NOT_FOUND, ex.getErrorCode(), "");
    }

    @ExceptionHandler(value = {NotFoundException.class})
    ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException ex) {
        return getErrorResponseEntity(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getEntity());
    }

    @ExceptionHandler(value = {NotAuthorizedException.class})
    ResponseEntity<ErrorResponse> handleNotAuthorizedException(NotAuthorizedException ex) {
        return getErrorResponseEntity(HttpStatus.UNAUTHORIZED, ex.getErrorCode(), "");
    }

    @ExceptionHandler(value = {AuthorizationDeniedException.class})
    ResponseEntity<ErrorResponse> handleNotForbiddenException(AuthorizationDeniedException ex) {
        return getErrorResponseEntity(HttpStatus.FORBIDDEN, ACCESS_FORBIDDEN, "");
    }

    @ExceptionHandler(value = {RoleAlreadyExistsException.class})
    ResponseEntity<ErrorResponse> handleRoleAlreadyExistsException(RoleAlreadyExistsException ex) {
        return getErrorResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR, ex.getErrorCode(), ex.getEntity());
    }

    @ExceptionHandler(value = {UserAlreadyExistsException.class})
    ResponseEntity<ErrorResponse> handleUserAlreadyExistsException(UserAlreadyExistsException ex) {
        return getErrorResponseEntity(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getEntity());
    }

    @ExceptionHandler(value = {InvalidClientException.class})
    ResponseEntity<ErrorResponse> handleInvalidClientException(InvalidClientException ex) {
        return getErrorResponseEntity(HttpStatus.BAD_REQUEST, ex.getErrorCode(), "");
    }

    @ExceptionHandler(value = {InvalidCredentialsException.class})
    ResponseEntity<ErrorResponse> handleInvalidCredentialsException(InvalidCredentialsException ex) {
        return getErrorResponseEntity(HttpStatus.UNAUTHORIZED, ex.getErrorCode(), "");
    }

    @ExceptionHandler(value = {KeycloakAdminClientException.class})
    ResponseEntity<ErrorResponse> handleKeycloakAdminClientException(KeycloakAdminClientException ex) {
        return getErrorResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR, ex.getErrorCode(), ex.getEntity());
    }

    @ExceptionHandler(value = {UnhandledException.class})
    ResponseEntity<ErrorResponse> handleUMUnhandledException(UnhandledException ex) {
        return getErrorResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR, ex.getErrorCode(), "");
    }

    @ExceptionHandler(value = {MissingConfigurationException.class})
    ResponseEntity<ErrorResponse> handleMissingConfigurationException(MissingConfigurationException ex) {
        return getErrorResponseEntity(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getEntity());
    }

    @ExceptionHandler(value = {MissingRequestParamException.class})
    ResponseEntity<ErrorResponse> handleMissingRequestParamException(MissingRequestParamException ex) {
        return getErrorResponseEntity(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getEntity());
    }

    private ResponseEntity<ErrorResponse> getErrorResponseEntity(HttpStatus status, String errorCode, String entity) {
        String message = messageUtil.getMessage(errorCode, new Object[]{}, LocaleContextHolder.getLocale());
        var errorDetails = new ErrorDetails(errorCode, message, entity);
        var headers = new HttpHeaders();
        headers.set("Content-type", MediaType.APPLICATION_JSON_VALUE);
        return ResponseEntity
                .status(status)
                .headers(headers)
                .body(new ErrorResponse(OffsetDateTime.now().toString(), status.value(), errorCode, message, errorDetails));
    }
}

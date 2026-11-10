package com.usermanagement.exception;

import java.time.OffsetDateTime;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.usermanagement.entity.ErrorResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts exceptions into the JSON {@link ErrorResponse}. Messages are translated using Accept-Language.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(UserManagementException.class)
    ResponseEntity<ErrorResponse> handleUserManagementException(UserManagementException ex) {
        log.warn("Request failed: {}", ex.getMessage());
        return buildResponse(ex.getErrorCode(), ex.getEntity());
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AuthorizationDeniedException ex) {
        return buildResponse(ErrorCode.ACCESS_FORBIDDEN, "");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return buildResponse(ErrorCode.INVALID_REQUEST, ex.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return buildResponse(ErrorCode.INVALID_REQUEST, "request body");
    }

    private ResponseEntity<ErrorResponse> buildResponse(ErrorCode errorCode, String entity) {
        String message = messageSource.getMessage(errorCode.getCode(), null, errorCode.name(),
                LocaleContextHolder.getLocale());
        var details = new ErrorResponse.ErrorDetails(errorCode.getCode(), message, entity);
        var body = new ErrorResponse(OffsetDateTime.now().toString(), errorCode.getHttpStatus().value(),
                errorCode.getCode(), message, details);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }
}

package com.usermanagement.entity;

public record ErrorResponse(String timestamp, int status, String errorCode, String message, ErrorDetails details) {

    public record ErrorDetails(String code, String message, String entity) {
    }
}

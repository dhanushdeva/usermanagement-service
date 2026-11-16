package com.usermanagement.exception;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Error body: {@code {"timestamp","httpStatus","errorCode","message","details":{"errorCode","message","entity"}}}.
 */
@Getter
@AllArgsConstructor
public class ErrorResponse implements Serializable {

    private static final long serialVersionUID = 1L;
    private String timestamp;
    private int httpStatus;
    private String errorCode;
    private String message;
    private ErrorDetails details;
}

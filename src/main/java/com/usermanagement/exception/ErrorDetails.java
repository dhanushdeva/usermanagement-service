package com.usermanagement.exception;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorDetails implements Serializable {

    private static final long serialVersionUID = 1L;
    private String errorCode;
    private String message;
    private String entity;
}

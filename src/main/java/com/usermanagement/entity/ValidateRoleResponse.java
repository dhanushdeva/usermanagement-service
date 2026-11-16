package com.usermanagement.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ValidateRoleResponse {

    private int status;
    private String message;
}

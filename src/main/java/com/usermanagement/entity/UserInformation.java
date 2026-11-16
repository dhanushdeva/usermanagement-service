package com.usermanagement.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserInformation {

    private Boolean isEmailVerified = false;
    private Boolean isPasswordSet = false;
}

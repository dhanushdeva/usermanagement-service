package com.usermanagement.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(name = "RegisterUserRequest", description = "Containing user data and configuration details required to register a new user")
public class RegisterUserRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "User data for registration", requiredMode = Schema.RequiredMode.REQUIRED)
    private RegisterUser user;
}

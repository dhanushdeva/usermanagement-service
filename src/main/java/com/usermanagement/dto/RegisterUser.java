package com.usermanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.NoArgsConstructor;

/** User data of the register request. Same fields as {@link User}; only the required ones differ in Swagger. */
@NoArgsConstructor
@Schema(name = "RegisterUser", description = "User data for registration",
        requiredProperties = {"userName", "email", "roles", "status"})
public class RegisterUser extends User {

    private static final long serialVersionUID = 1L;
}

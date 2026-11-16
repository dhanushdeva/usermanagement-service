package com.usermanagement.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(name = "UpdateUserRequest", description = "Containing the user data to update")
public class UpdateUserRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "User data to update", requiredMode = Schema.RequiredMode.REQUIRED)
    private UpdateUser user;
}

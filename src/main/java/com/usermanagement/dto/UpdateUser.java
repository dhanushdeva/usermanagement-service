package com.usermanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.NoArgsConstructor;

/** User data of the update request: only userId is required, every other field is optional. */
@NoArgsConstructor
@Schema(name = "UpdateUser", description = "User data to update. Only userId is required; send only the fields to change",
        requiredProperties = {"userId"})
public class UpdateUser extends User {

    private static final long serialVersionUID = 1L;
}

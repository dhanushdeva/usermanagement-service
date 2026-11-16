package com.usermanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(name = "ChangeUserStatus", description = "For updating the user details")
public class ChangeUserStatusRequest {

    @Schema(description = "Unique Identifier of the User", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;
    @Schema(description = "Change User Status, ENABLE or DISABLE in upper case",
            requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"ENABLE", "DISABLE"}, example = "ENABLE")
    private String status;
}

package com.usermanagement.dto;

import java.util.Properties;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(name = "ValidateRoleRequest", description = "Containing configuration details required to validate a user's role with the provider")
public class ValidateRoleRequest {

    @Schema(description = "Configurations to connect to a provider. For e.g., in case of Keycloak user.realm has to be provided",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Properties configurations;
}

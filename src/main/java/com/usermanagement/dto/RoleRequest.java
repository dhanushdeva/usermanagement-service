package com.usermanagement.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(name = "RoleRequest", description = "Containing role information required for role creation with the provider")
public class RoleRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Role Information for creation", requiredMode = Schema.RequiredMode.REQUIRED)
    private Roles roles;
}

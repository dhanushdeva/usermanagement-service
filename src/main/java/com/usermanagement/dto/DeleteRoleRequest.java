package com.usermanagement.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(name = "DeleteRoleRequest", description = "Containing configuration details and the name of the role that needs to be deleted")
public class DeleteRoleRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "The name of the role to be deleted", requiredMode = Schema.RequiredMode.REQUIRED)
    private String roleName;
}

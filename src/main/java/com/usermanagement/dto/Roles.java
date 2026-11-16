package com.usermanagement.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(name = "Roles", description = "User Roles/permissions")
public class Roles implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "The name of the Role (case sensitive)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(description = "The description of the Role")
    private String description;
}

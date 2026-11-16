package com.usermanagement.entity;

import lombok.Getter;
import lombok.Setter;

/** Keycloak RoleRepresentation. */
@Getter
@Setter
public class KeycloakDTORoles {

    private String id;
    private String name;
    private String description;
    private String containerId;
    private Boolean composite;
    private boolean clientRole;
}

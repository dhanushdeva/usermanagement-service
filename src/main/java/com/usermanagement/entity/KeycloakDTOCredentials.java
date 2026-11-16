package com.usermanagement.entity;

import lombok.Getter;
import lombok.Setter;

/** Keycloak CredentialRepresentation. */
@Getter
@Setter
public class KeycloakDTOCredentials {

    private String id;
    private String type;
    private String value;
    private boolean temporary;
}

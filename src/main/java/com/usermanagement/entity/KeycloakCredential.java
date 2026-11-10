package com.usermanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Keycloak Admin API "CredentialRepresentation".
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeycloakCredential {
    public static final String TYPE_PASSWORD = "password";

    private String id;
    private String type;
    private String value;
    private Boolean temporary;

    public static KeycloakCredential password(String value, boolean temporary) {
        KeycloakCredential credential = new KeycloakCredential();
        credential.setType(TYPE_PASSWORD);
        credential.setValue(value);
        credential.setTemporary(temporary);
        return credential;
    }
}

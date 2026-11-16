package com.usermanagement.entity;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/** Keycloak UserRepresentation (only the fields this service uses). */
@Getter
@Setter
public class KeycloakDTOUser {

    private String id;
    private String username;
    private Boolean enabled;
    private Boolean emailVerified;
    private String firstName;
    private String lastName;
    private String email;
    private List<KeycloakDTOCredentials> credentials;
    private Map<String, List<String>> attributes;
    private long createdTimestamp;
}

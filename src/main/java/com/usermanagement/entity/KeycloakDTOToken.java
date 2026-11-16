package com.usermanagement.entity;

import lombok.Getter;
import lombok.Setter;

/** Keycloak token endpoint response (field names as returned by Keycloak). */
@Getter
@Setter
public class KeycloakDTOToken {

    private String access_token;
    private int expires_in;
    private int refresh_expires_in;
    private String refresh_token;
    private String token_type;
    private String id_token;
    private String session_state;
    private String scope;
}

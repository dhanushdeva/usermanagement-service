package com.usermanagement.entity;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KeycloakDTOList {

    private List<KeycloakDTORoles> roles;
    private List<KeycloakDTOUser> users;
    private List<KeycloakDTOCredentials> credentials;
}

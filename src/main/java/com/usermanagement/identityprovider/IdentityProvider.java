package com.usermanagement.identityprovider;

import java.util.List;

import com.usermanagement.constants.UserStatus;
import com.usermanagement.dto.RoleDto;
import com.usermanagement.dto.UserDto;
import com.usermanagement.entity.AnonymousUserResponse;

/**
 * Everything the service needs from an identity provider. Keycloak is the only implementation
 * ({@link com.usermanagement.identityprovider.keycloak.KeycloakIdentityProvider}); another IdP can be plugged in
 * by implementing this interface.
 */
public interface IdentityProvider {

    // users
    AnonymousUserResponse createAnonymousUser();

    String registerUser(UserDto user);

    UserDto updateUser(UserDto user);

    void deleteUsers(List<String> userIds);

    void changeUserStatus(String userId, UserStatus status);

    // realm roles
    String createRole(RoleDto role);

    void deleteRole(String roleName);
}

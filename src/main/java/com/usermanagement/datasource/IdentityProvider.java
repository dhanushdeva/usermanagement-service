package com.usermanagement.datasource;

import java.util.List;

import com.usermanagement.dto.Roles;
import com.usermanagement.dto.User;
import com.usermanagement.entity.AnonymousUser;
import com.usermanagement.entity.RoleDetails;
import com.usermanagement.entity.UserInformation;

/**
 * Operations every identity provider implements (same as bm-usermanagement-v4). Keycloak is the only one:
 * {@link com.usermanagement.datasource.provider.keycloak.KeycloakServiceProvider}.
 */
public interface IdentityProvider {

    AnonymousUser createAnonymousUser();

    String registerUser(User user);

    void deleteUsers(List<String> userIds);

    void retrieveAndDeleteAnonymousUser();

    String addRole(Roles roles);

    String deleteRole(String roleName);

    boolean validateRole(String roleToValidate);

    User updateUser(User user);

    Boolean changeUserStatus(String userId, String status);

    UserInformation getUserInformation(String userId);

    void resendVerificationMail(String userId);

    List<User> getUsers(String search);

    User getUserById(String userId);

    RoleDetails getRoleAndComposite(String role);

    RoleDetails createRoleIfNotExist(String role);
}

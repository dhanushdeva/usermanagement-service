package com.usermanagement.service;

import java.util.List;

import com.usermanagement.dto.Roles;
import com.usermanagement.dto.User;
import com.usermanagement.entity.AnonymousUser;
import com.usermanagement.entity.RoleDetails;
import com.usermanagement.entity.UserInformation;

/**
 * Operations of the user management service (same as bm-usermanagement-v4).
 */
public interface UserManagementService {

    AnonymousUser createAnonymousUser();

    String registerUser(User user);

    void deleteUsers(List<String> userIds);

    void retrieveAndDeleteAnonymousUser();

    String addRole(Roles roles);

    String deleteRole(String roleName);

    boolean validateRole(String roleToValidate);

    RoleDetails createRoleIfNotExist(String role);

    RoleDetails getRoleAndComposite(String role);

    User updateUser(User user);

    void resendVerificationMail(String userId);

    Boolean changeUserStatus(String userId, String status);

    UserInformation getUserInformation(String userId);

    List<User> getUsers(String search);

    User getUserById(String userId);
}

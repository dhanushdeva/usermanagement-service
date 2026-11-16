package com.usermanagement.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usermanagement.datasource.IdentityProvider;
import com.usermanagement.dto.Roles;
import com.usermanagement.dto.User;
import com.usermanagement.entity.AnonymousUser;
import com.usermanagement.entity.RoleDetails;
import com.usermanagement.entity.UserInformation;
import com.usermanagement.service.UserManagementService;

/**
 * Delegates every call to the configured {@link IdentityProvider} (same as bm-usermanagement-v4).
 */
@Service("userManagementService")
public class UserManagementServiceImpl implements UserManagementService {

    private final IdentityProvider idp;

    public UserManagementServiceImpl(IdentityProvider idp) {
        this.idp = idp;
    }

    @Override
    public AnonymousUser createAnonymousUser() {
        return idp.createAnonymousUser();
    }

    @Override
    public String registerUser(User user) {
        return idp.registerUser(user);
    }

    @Override
    public void deleteUsers(List<String> userIds) {
        idp.deleteUsers(userIds);
    }

    @Override
    public void retrieveAndDeleteAnonymousUser() {
        idp.retrieveAndDeleteAnonymousUser();
    }

    @Override
    public String addRole(Roles roles) {
        return idp.addRole(roles);
    }

    @Override
    public String deleteRole(String roleName) {
        return idp.deleteRole(roleName);
    }

    @Override
    public boolean validateRole(String roleToValidate) {
        return idp.validateRole(roleToValidate);
    }

    @Override
    public RoleDetails createRoleIfNotExist(String role) {
        return idp.createRoleIfNotExist(role);
    }

    @Override
    public RoleDetails getRoleAndComposite(String role) {
        return idp.getRoleAndComposite(role);
    }

    @Override
    public User updateUser(User user) {
        return idp.updateUser(user);
    }

    @Override
    public void resendVerificationMail(String userId) {
        idp.resendVerificationMail(userId);
    }

    @Override
    public Boolean changeUserStatus(String userId, String status) {
        return idp.changeUserStatus(userId, status);
    }

    @Override
    public UserInformation getUserInformation(String userId) {
        return idp.getUserInformation(userId);
    }

    @Override
    public List<User> getUsers(String search) {
        return idp.getUsers(search);
    }

    @Override
    public User getUserById(String userId) {
        return idp.getUserById(userId);
    }
}

package com.usermanagement.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usermanagement.constants.UserStatus;
import com.usermanagement.dto.RoleDto;
import com.usermanagement.dto.UserDto;
import com.usermanagement.entity.AnonymousUserResponse;
import com.usermanagement.identityprovider.IdentityProvider;
import com.usermanagement.service.UserManagementService;

import lombok.RequiredArgsConstructor;

/**
 * Delegates to the configured {@link IdentityProvider}; add cross-cutting rules (auditing, events...) here so they
 * stay independent of Keycloak.
 */
@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

    private final IdentityProvider identityProvider;

    @Override
    public AnonymousUserResponse createAnonymousUser() {
        return identityProvider.createAnonymousUser();
    }

    @Override
    public String registerUser(UserDto user) {
        return identityProvider.registerUser(user);
    }

    @Override
    public UserDto updateUser(UserDto user) {
        return identityProvider.updateUser(user);
    }

    @Override
    public void deleteUsers(List<String> userIds) {
        identityProvider.deleteUsers(userIds);
    }

    @Override
    public void changeUserStatus(String userId, UserStatus status) {
        identityProvider.changeUserStatus(userId, status);
    }

    @Override
    public String createRole(RoleDto role) {
        return identityProvider.createRole(role);
    }

    @Override
    public void deleteRole(String roleName) {
        identityProvider.deleteRole(roleName);
    }
}

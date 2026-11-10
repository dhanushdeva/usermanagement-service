package com.usermanagement.service;

import java.util.List;

import com.usermanagement.constants.UserStatus;
import com.usermanagement.dto.RoleDto;
import com.usermanagement.dto.UserDto;
import com.usermanagement.entity.AnonymousUserResponse;

/**
 * Business operations exposed by the user management API.
 */
public interface UserManagementService {

    AnonymousUserResponse createAnonymousUser();

    String registerUser(UserDto user);

    UserDto updateUser(UserDto user);

    void deleteUsers(List<String> userIds);

    void changeUserStatus(String userId, UserStatus status);

    String createRole(RoleDto role);

    void deleteRole(String roleName);
}

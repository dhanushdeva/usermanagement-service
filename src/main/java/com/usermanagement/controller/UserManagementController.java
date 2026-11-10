package com.usermanagement.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usermanagement.constants.UserStatus;
import com.usermanagement.dto.RoleDto;
import com.usermanagement.dto.UserDto;
import com.usermanagement.dto.ChangeUserStatusRequest;
import com.usermanagement.dto.CreateRoleRequest;
import com.usermanagement.dto.DeleteRoleRequest;
import com.usermanagement.dto.DeleteUsersRequest;
import com.usermanagement.dto.UserRequest;
import com.usermanagement.entity.AnonymousUserResponse;
import com.usermanagement.exception.ErrorCode;
import com.usermanagement.exception.UserManagementException;
import com.usermanagement.service.UserManagementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * REST API - the same seven endpoints as bm-usermanagement-service. Each protected endpoint is guarded by
 * {@code app.security.api-roles.<key>} in application.yaml.
 */
@RestController
@RequestMapping("/usermanagement")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "User Management")
@SecurityRequirement(name = "bearerAuth")
public class UserManagementController {

    private final UserManagementService userManagementService;

    @Operation(summary = "Update existing user")
    @PreAuthorize("@apiAccess.isAllowed('update-user')")
    @PostMapping("/updateUser")
    public UserDto updateUser(@RequestBody UserRequest request) {
        UserDto user = requireUser(request);
        requireUserId(user.getUserId());
        return userManagementService.updateUser(user);
    }

    @Operation(summary = "Register/Add a new user")
    @PreAuthorize("@apiAccess.isAllowed('register-user')")
    @PostMapping("/register")
    public ResponseEntity<UserDto> registerUser(@RequestBody UserRequest request) {
        UserDto user = requireUser(request);
        if (!StringUtils.hasText(user.getUserName())) {
            throw new UserManagementException(ErrorCode.MISSING_USER_DETAILS, "userName");
        }
        user.setUserId(userManagementService.registerUser(user));
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @Operation(summary = "Change User Status")
    @PreAuthorize("@apiAccess.isAllowed('change-user-status')")
    @PostMapping("/changeUserStatus")
    public ResponseEntity<Void> changeUserStatus(@RequestBody ChangeUserStatusRequest request) {
        requireUserId(request.userId());
        UserStatus status = UserStatus.fromValue(request.status())
                .orElseThrow(() -> new UserManagementException(ErrorCode.INVALID_USER_STATUS, request.status()));
        userManagementService.changeUserStatus(request.userId(), status);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Obtain an anonymous token", security = {})
    @PostMapping("/anonymous")
    public AnonymousUserResponse createAnonymousUser() {
        return userManagementService.createAnonymousUser();
    }

    @Operation(summary = "Add a Role to the user")
    @PreAuthorize("@apiAccess.isAllowed('create-role')")
    @PostMapping("/addRole")
    public ResponseEntity<RoleDto> createRole(@RequestBody CreateRoleRequest request) {
        RoleDto role = request.role();
        if (role == null || !StringUtils.hasText(role.name())) {
            throw new UserManagementException(ErrorCode.MISSING_ROLE_DETAILS, "roles.name");
        }
        userManagementService.createRole(role);
        return ResponseEntity.status(HttpStatus.CREATED).body(role);
    }

    @Operation(summary = "Delete a list of users")
    @PreAuthorize("@apiAccess.isAllowed('delete-users')")
    @DeleteMapping("/deleteUsers")
    public ResponseEntity<Void> deleteUsers(@RequestBody DeleteUsersRequest request) {
        if (request == null || request.userIds() == null || request.userIds().isEmpty()) {
            throw new UserManagementException(ErrorCode.MISSING_USER_IDS, "userIds");
        }
        userManagementService.deleteUsers(request.userIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a Role")
    @PreAuthorize("@apiAccess.isAllowed('delete-role')")
    @DeleteMapping("/deleteRole")
    public ResponseEntity<Void> deleteRole(@RequestBody DeleteRoleRequest request) {
        if (!StringUtils.hasText(request.roleName())) {
            throw new UserManagementException(ErrorCode.MISSING_ROLE_NAME, "roleName");
        }
        userManagementService.deleteRole(request.roleName());
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- helpers

    private static UserDto requireUser(UserRequest request) {
        if (request == null || request.user() == null) {
            throw new UserManagementException(ErrorCode.MISSING_USER_DETAILS, "user");
        }
        return request.user();
    }

    private static void requireUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new UserManagementException(ErrorCode.MISSING_USER_ID, "userId");
        }
    }
}

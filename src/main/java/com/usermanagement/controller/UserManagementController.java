package com.usermanagement.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.usermanagement.constants.UsermanagementConstants;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import com.usermanagement.dto.ChangeUserStatusRequest;
import com.usermanagement.dto.DeleteRoleRequest;
import com.usermanagement.dto.DeleteUsersRequest;
import com.usermanagement.dto.RegisterUserRequest;
import com.usermanagement.dto.RoleRequest;
import com.usermanagement.dto.Roles;
import com.usermanagement.dto.UpdateUserRequest;
import com.usermanagement.dto.User;
import com.usermanagement.dto.ValidateRoleRequest;
import com.usermanagement.entity.AnonymousUser;
import com.usermanagement.entity.RoleDetails;
import com.usermanagement.entity.UserInformation;
import com.usermanagement.entity.ValidateRoleResponse;
import com.usermanagement.exception.MissingConfigurationException;
import com.usermanagement.exception.MissingRequestParamException;
import com.usermanagement.exception.UMErrorCodeConstants;
import com.usermanagement.service.UserManagementService;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API - same 15 endpoints, request bodies, validations and error codes as bm-usermanagement-v4.
 * 7 endpoints are visible in Swagger; the other 8 are {@link Hidden} exactly like in the original (still callable).
 * Permissions: {@code @PreAuthorize("hasAnyRole(@securityConfiguration.getXxx())")} with the roles configured in
 * {@code apipermission.api.*} (application.yaml).
 */
@Slf4j
@RestController
@RequestMapping("/usermanagement")
@Tag(name = "/usermanagement", description = "User Management")
public class UserManagementController {

    private static final String ROLE_NAME = "role.name";
    private static final String ROLE_FOUND = "Role Found";
    private static final String ROLE_NOT_FOUND = "Role Not Found";
    private static final String MISSING_DETAILS = "User details not provided in the request";
    private static final String MISSING_USER_IDS = "User Ids list not provided in the request";
    private static final String MISSING_USER_ID = "UserId not provided in the request";
    private static final String MISSING_ROLE_DETAILS = "Role details not provided in the request";

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @Operation(summary = "Obtain an anonymous token", description = "Generates a temporary access token for guest or unauthenticated access scenarios. Provides limited permissions without requiring full user registration.")
    @SecurityRequirements
    @PostMapping(value = "/anonymous", produces = {"application/json"})
    public ResponseEntity<AnonymousUser> anonymous() {
        log.info("Request received to create an anonymous user");
        return new ResponseEntity<>(userManagementService.createAnonymousUser(), HttpStatus.OK);
    }

    @Operation(summary = "Register/Add a new user", description = "Creates a new user account with provided credentials and profile information.")
    @PreAuthorize("hasAnyRole(@securityConfiguration.getRegister())")
    @PostMapping(value = "/register", produces = {"application/json"})
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<User> register(@RequestBody RegisterUserRequest request) {
        log.info("Request received to register a new user");
        if (null == request.getUser()) {
            log.error(MISSING_DETAILS);
            throw new MissingRequestParamException(UMErrorCodeConstants.MISSING_USER_DETAILS, MISSING_DETAILS, "user");
        }
        var user = request.getUser();
        if (user.getUserName() == null || user.getUserName().isBlank()) {
            log.error("User name not provided in the request");
            throw new MissingRequestParamException(UMErrorCodeConstants.MISSING_USER_NAME,
                    "User name not provided in the request", "userName");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.error("Email not provided in the request");
            throw new MissingRequestParamException(UMErrorCodeConstants.MISSING_USER_EMAIL,
                    "Email not provided in the request", "email");
        }
        if (user.getRoles() == null) {
            log.error("Roles not provided in the request");
            throw new MissingRequestParamException(UMErrorCodeConstants.MISSING_USER_ROLES,
                    "Roles not provided in the request", "roles");
        }
        if (user.getStatus() == null || !("ENABLE".equals(user.getStatus()) || "DISABLE".equals(user.getStatus()))) {
            log.error("Status missing or not ENABLE/DISABLE");
            throw new MissingRequestParamException(UMErrorCodeConstants.MISSING_USER_STATUS,
                    "Status must be ENABLE or DISABLE", "status");
        }
        String userId = userManagementService.registerUser(user);
        user.setUserId(userId);
        return new ResponseEntity<>(user, HttpStatus.CREATED);
    }

    @Operation(summary = "Update existing user", description = "Modifies an existing user's profile and attributes in the identity management system. Returns updated user details upon successful modification.")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole(@securityConfiguration.getUpdateUser())")
    @PostMapping(value = "/updateUser", produces = {"application/json"})
    public ResponseEntity<User> updateUser(@RequestBody UpdateUserRequest request) {
        log.info("Request received to update a user");
        if (null == request.getUser()) {
            log.error(MISSING_DETAILS);
            throw new MissingRequestParamException(UMErrorCodeConstants.MISSING_USER_DETAILS, MISSING_DETAILS, "user");
        }
        if (request.getUser().getUserId() == null || "".equalsIgnoreCase(request.getUser().getUserId())) {
            log.error(MISSING_USER_ID);
            throw new MissingRequestParamException(UMErrorCodeConstants.MISSING_USER_IDS, MISSING_USER_ID, "user");
        }
        return new ResponseEntity<>(userManagementService.updateUser(request.getUser()), HttpStatus.OK);
    }

    @Operation(summary = "Delete a list of users", description = "Permanently removes multiple user accounts from the system in a single operation.")
    @DeleteMapping(value = "/deleteUsers", produces = {"application/json"})
    @PreAuthorize("hasAnyRole(@securityConfiguration.getDeleteUsers())")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Object> deleteUsers(@RequestBody DeleteUsersRequest request) {
        log.info("Request received to delete a list of users");
        if (null == request.getUserIds()) {
            log.error(MISSING_USER_IDS);
            throw new MissingRequestParamException(UMErrorCodeConstants.MISSING_USER_IDS, MISSING_USER_ID, "user");
        }
        userManagementService.deleteUsers(request.getUserIds());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Hidden
    @Operation(summary = "Retrieve & Delete Anonymous User", description = "Identifies and removes anonymous user accounts that have exceeded their validity period.")
    @GetMapping(value = "/retrieveAndDeleteAnonymousUser")
    @PreAuthorize("hasAnyRole(@securityConfiguration.getRetrieveAndDeleteAnonymousUser())")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Object> retrieveAndDeleteAnonymousUser() {
        log.info("Request received to retrieve and delete anonymous user");
        userManagementService.retrieveAndDeleteAnonymousUser();
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Hidden
    @Operation(summary = "Validate IDP Role", description = "Verifies whether a specified role exists in the identity provider's role registry. Returns validation status to confirm role availability before assignment operations.")
    @PreAuthorize("hasAnyRole(@securityConfiguration.getValidateIdpRole())")
    @PostMapping(value = "/validateIdpRole")
    public ResponseEntity<ValidateRoleResponse> validateIdpRole(@RequestBody ValidateRoleRequest request) {
        log.info("Request received to validate IDP role");
        if (null == request.getConfigurations().getProperty(ROLE_NAME)
                || request.getConfigurations().getProperty(ROLE_NAME).isEmpty()) {
            log.error("role.name is missing");
            throw new MissingConfigurationException(UMErrorCodeConstants.MISSING_ROLE_NAME, "role.name is missing", ROLE_NAME);
        }
        String roleToValidate = request.getConfigurations().getProperty(ROLE_NAME);
        boolean isRolePresent = userManagementService.validateRole(roleToValidate);
        if (isRolePresent) {
            return new ResponseEntity<>(new ValidateRoleResponse(HttpStatus.OK.value(), ROLE_FOUND), HttpStatus.OK);
        }
        return new ResponseEntity<>(new ValidateRoleResponse(HttpStatus.NOT_FOUND.value(), ROLE_NOT_FOUND), HttpStatus.NOT_FOUND);
    }

    @Operation(summary = "Add a Role to the user", description = "Assigns an additional authorization role to an existing user account. Expands the user's access permissions across the application.")
    @PostMapping(value = "/addRole", produces = {"application/json"})
    @PreAuthorize("hasAnyRole(@securityConfiguration.getAddRole())")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Roles> addRole(@RequestBody RoleRequest request) {
        log.info("Request received to add a role");
        if (null == request.getRoles() || null == request.getRoles().getName()) {
            log.error(MISSING_ROLE_DETAILS);
            throw new MissingRequestParamException(UMErrorCodeConstants.MISSING_ROLE_DETAILS, MISSING_ROLE_DETAILS, "roles");
        }
        var roles = request.getRoles();
        userManagementService.addRole(roles);
        return new ResponseEntity<>(roles, HttpStatus.CREATED);
    }

    @Operation(summary = "Delete a Role", description = "Removes an assigned role from a user account, revoking the associated permissions and access rights granted by that role.")
    @PreAuthorize("hasAnyRole(@securityConfiguration.getDeleteRole())")
    @DeleteMapping(value = "/deleteRole", produces = {"application/json"})
    public ResponseEntity<String> deleteRole(@RequestBody DeleteRoleRequest roleRequest) {
        log.info("Request received to delete a role");
        if (null == roleRequest.getRoleName()) {
            log.error(MISSING_DETAILS);
            throw new MissingRequestParamException(UMErrorCodeConstants.MISSING_ROLE_DETAILS, MISSING_ROLE_DETAILS, "roles");
        }
        userManagementService.deleteRole(roleRequest.getRoleName());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(summary = "Change User Status", description = "Modifies the activation status of a user account, enabling or disabling access based on the specified status.")
    @PreAuthorize("hasAnyRole(@securityConfiguration.getChangeUserStatus())")
    @PostMapping(value = "/changeUserStatus", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> changeUserStatus(@RequestBody ChangeUserStatusRequest request) {
        log.info("Request received to change user status");
        if (null == request.getUserId()) {
            log.error(MISSING_DETAILS);
            throw new MissingRequestParamException(UMErrorCodeConstants.MISSING_USER_ID, MISSING_DETAILS, "user.id");
        } else if (null == request.getStatus()
                || (!request.getStatus().equals(UsermanagementConstants.USER_STATUS_ENABLE)
                && !request.getStatus().equals(UsermanagementConstants.USER_STATUS_DISABLE))) {
            log.error(MISSING_DETAILS);
            throw new MissingRequestParamException(UMErrorCodeConstants.MISSING_USER_STATUS, MISSING_DETAILS, "status");
        }
        userManagementService.changeUserStatus(request.getUserId(), request.getStatus());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Hidden
    @Operation(summary = "Fetch role and composite role", description = "Retrieves detailed information about a specific role, including any composite roles that are associated with it.")
    @PreAuthorize("hasAnyRole(@securityConfiguration.getRoleAndComposite())")
    @GetMapping(value = "/getRoleAndComposite", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<RoleDetails> getRoleAndComposite(@RequestParam String roleName) {
        log.info("Request received to fetch role and composite role");
        var roleDetails = userManagementService.getRoleAndComposite(roleName);
        return new ResponseEntity<>(roleDetails, HttpStatus.OK);
    }

    @Hidden
    @Operation(summary = "Fetch user information")
    @PreAuthorize("hasAnyRole(@securityConfiguration.getUserInformation())")
    @GetMapping(value = "/getUserInformation", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<UserInformation> getUserInformation(@RequestParam String userId) {
        log.info("Request received to fetch user information");
        var userInformation = userManagementService.getUserInformation(userId);
        return new ResponseEntity<>(userInformation, HttpStatus.OK);
    }

    @Hidden
    @Operation(summary = "Resend verification mail")
    @PreAuthorize("hasAnyRole(@securityConfiguration.getResendVerificationMail())")
    @PostMapping(value = "/resendVerificationMail", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> resendVerificationMail(@RequestParam String userId) {
        log.info("Request received to resend verification mail");
        userManagementService.resendVerificationMail(userId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Hidden
    @Operation(summary = "Fetch users")
    @PreAuthorize("hasAnyRole(@securityConfiguration.getUsers())")
    @GetMapping(value = "/getUsers", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<User>> getUsers(@RequestParam String search) {
        log.info("Request received to fetch users");
        List<User> userList = userManagementService.getUsers(search);
        return new ResponseEntity<>(userList, HttpStatus.OK);
    }

    @Hidden
    @Operation(summary = "Fetch user by id")
    @PreAuthorize("hasAnyRole(@securityConfiguration.getUsers())")
    @GetMapping(value = "/getUserById", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<User> getUserById(@RequestParam String userId) {
        log.info("Request received to fetch user by id");
        var user = userManagementService.getUserById(userId);
        return new ResponseEntity<>(user, HttpStatus.OK);
    }

    /** No @PreAuthorize in the original either: any valid (authenticated) token may call it. */
    @Hidden
    @Operation(summary = "Fetch role and composite role")
    @GetMapping(value = "/createRoleIfNotExist", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<RoleDetails> createRoleIfNotExist(@RequestParam String roleName) {
        log.info("Request received to fetch role and composite role");
        var roleDetails = userManagementService.createRoleIfNotExist(roleName);
        return new ResponseEntity<>(roleDetails, HttpStatus.OK);
    }
}

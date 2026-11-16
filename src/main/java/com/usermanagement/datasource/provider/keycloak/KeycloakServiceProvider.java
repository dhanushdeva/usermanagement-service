package com.usermanagement.datasource.provider.keycloak;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.net.ssl.SSLHandshakeException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import com.usermanagement.constants.KeycloakConstants;
import com.usermanagement.datasource.IdentityProvider;
import com.usermanagement.dto.Attribute;
import com.usermanagement.dto.Roles;
import com.usermanagement.dto.User;
import com.usermanagement.entity.AnonymousUser;
import com.usermanagement.entity.KeycloakDTOCredentials;
import com.usermanagement.entity.KeycloakDTOList;
import com.usermanagement.entity.KeycloakDTORoles;
import com.usermanagement.entity.KeycloakDTOToken;
import com.usermanagement.entity.KeycloakDTOUser;
import com.usermanagement.entity.RoleDetails;
import com.usermanagement.entity.Token;
import com.usermanagement.entity.UserInformation;
import com.usermanagement.exception.ConnectionTimeOutException;
import com.usermanagement.exception.ErrorCodeConstants;
import com.usermanagement.exception.InvalidClientException;
import com.usermanagement.exception.InvalidConfigException;
import com.usermanagement.exception.InvalidCredentialsException;
import com.usermanagement.exception.KeycloakAdminClientException;
import com.usermanagement.exception.NotFoundException;
import com.usermanagement.exception.RoleAlreadyExistsException;
import com.usermanagement.exception.UMErrorCodeConstants;
import com.usermanagement.exception.UnhandledException;
import com.usermanagement.exception.UserAlreadyExistsException;
import com.usermanagement.util.HttpHandler;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Keycloak implementation of {@link IdentityProvider}. Same flow as KeycloakServiceProvider in bm-usermanagement-v4:
 * every operation first gets an admin token (client credentials of the admin client), then calls the Keycloak
 * Admin REST API through {@link HttpHandler}; Keycloak HTTP errors are translated by throwUserException /
 * throwRoleException. Only the connection settings differ: they point to the local Keycloak (bm.idp.keycloak.*).
 */
@Slf4j
@Configuration
@ConditionalOnProperty(value = "bm.idp.provider", havingValue = "keycloak")
public class KeycloakServiceProvider implements IdentityProvider {

    private static final String CLIENT_ID_KEY = "client_id";
    private static final String CLIENT_SECRET_KEY = "client_secret";
    private static final String GRAND_TYPE_KEY = "grant_type";
    private static final String SCOPE_KEY = "scope";
    private static final String USERNAME_KEY = "username";
    private static final String PASSWORD_KEY = "password";
    private static final String REFRESH_TOKEN_KEY = "refresh_token";
    private static final String GRAND_TYPE_PASSWORD = "password";
    private static final String GRAND_TYPE_CLIENT_CREDENTIAL = "client_credentials";
    private static final String URL_TOKEN = "/realms/{{realm}}/protocol/openid-connect/token";
    private static final String URL_ROLES = "/admin/realms/{{realm}}/roles";
    private static final String URL_USER = "/admin/realms/{{realm}}/users";
    private static final String RUNTIME_EXCEPTION_MESSAGE = "RuntimeException occurred with message: {}";

    @Value("${bm.idp.keycloak.url}")
    private String serverUrl;

    @Value("${bm.idp.keycloak.enableEmailVerification}")
    private Boolean enableEmailVerification;

    @Value("${bm.idp.keycloak.email_lifespan}")
    private Integer emailLifespan;

    @Value("${bm.idp.keycloak.admin.realm}")
    private String adminRealm;

    @Value("${bm.idp.keycloak.admin.client_id}")
    private String adminClientId;

    @Value("${bm.idp.keycloak.admin.client_secret}")
    private String adminClientSecret;

    @Value("${bm.idp.keycloak.admin.redirect_url}")
    private String ssoRedirectUrl;

    @Value("${bm.idp.keycloak.anonymous.client_id}")
    private String anonymousClientId;

    @Value("${bm.idp.keycloak.anonymous.client_secret}")
    private String anonymousClientSecret;

    @Value("${bm.idp.keycloak.isTemporary}")
    private Boolean isTemporary;

    @Autowired
    private ObjectMapper mapper;

    // ------------------------------------------------------------------ roles

    /** Loads all realm roles and checks whether one has the given name. */
    @Override
    public boolean validateRole(String roleToValidate) {
        log.info("Validating role method started");
        String token = getAdminToken();
        List<KeycloakDTORoles> roles = getRoles(adminRealm, token);
        return roles.stream().anyMatch(n -> n.getName().equals(roleToValidate));
    }

    /** Returns the role and its composites; creates the role (empty description) when Keycloak says "not found". */
    @Override
    public RoleDetails createRoleIfNotExist(String role) {
        log.info("createRoleIfNotExist method started");
        String token = getAdminToken();
        KeycloakDTORoles roleObj;
        List<String> compositeRoles = new ArrayList<>();
        try {
            roleObj = getRole(adminRealm, token, role);
            if (Boolean.TRUE.equals(roleObj.getComposite())) {
                List<KeycloakDTORoles> compositeIDPRoles = getCompositeRoles(adminRealm, token, role);
                compositeRoles = compositeIDPRoles.stream().map(KeycloakDTORoles::getName).toList();
            }
        } catch (NotFoundException ex) {
            if (ex.getMessage().contains("Role not found")) {
                Roles createRole = new Roles();
                createRole.setName(role);
                createRole.setDescription("");
                addRole(createRole);
            }
        }
        RoleDetails response = new RoleDetails();
        response.setName(role);
        response.setComposite(compositeRoles);
        log.info("createRoleIfNotExist method end");
        return response;
    }

    @Override
    public RoleDetails getRoleAndComposite(String role) {
        log.info("Validating role method started");
        String token = getAdminToken();
        KeycloakDTORoles roleObj = getRole(adminRealm, token, role);
        List<String> compositeRoles = new ArrayList<>();
        if (Boolean.TRUE.equals(roleObj.getComposite())) {
            List<KeycloakDTORoles> compositeIDPRoles = getCompositeRoles(adminRealm, token, role);
            compositeRoles = compositeIDPRoles.stream().map(KeycloakDTORoles::getName).toList();
        }
        RoleDetails response = new RoleDetails();
        response.setName(role);
        response.setComposite(compositeRoles);
        return response;
    }

    @Override
    public String addRole(Roles roles) {
        log.info("Adding role method started");
        String token = getAdminToken();
        try {
            KeycloakDTORoles role = new KeycloakDTORoles();
            role.setName(roles.getName());
            role.setDescription(roles.getDescription());
            HttpHandler.post(getUrl(adminRealm, URL_ROLES), token, role);
            log.info("Role added successfully");
            return roles.getName();
        } catch (RestClientResponseException e) {
            throwRoleException(e, roles.getName());
        } catch (ResourceAccessException ex) {
            throwResourceException(ex);
        }
        return null;
    }

    @Override
    public String deleteRole(String roleName) {
        log.info("Deleting role method started");
        String token = getAdminToken();
        try {
            String url = getUrl(adminRealm, URL_ROLES, roleName);
            HttpHandler.del(url, token, null);
            log.info("Role deleted successfully");
            return "Success";
        } catch (RestClientResponseException e) {
            throwRoleException(e, roleName);
        } catch (ResourceAccessException e) {
            throwResourceException(e);
        }
        return "Failed";
    }

    // ------------------------------------------------------------------ users

    /** Token of the anonymous client (client credentials); nothing is stored in Keycloak. */
    @Override
    public AnonymousUser createAnonymousUser() {
        log.info("Creating anonymous user method started");
        String body = getTokenBody(anonymousClientId, anonymousClientSecret, GRAND_TYPE_CLIENT_CREDENTIAL, null, null, null, null);
        Token userToken;
        try {
            userToken = getToken(adminRealm, body);
        } catch (InvalidCredentialsException e) {
            log.error("Keycloak rejected the anonymous client '{}': check bm.idp.keycloak.anonymous client_id/client_secret in application.yaml",
                    anonymousClientId);
            throw new KeycloakAdminClientException(e.getMessage(), UMErrorCodeConstants.ANONYMOUS_CLIENT_LOGIN_FAILED, anonymousClientId);
        }
        var anonymousUser = new AnonymousUser();
        anonymousUser.setUserName(UUID.randomUUID().toString());
        anonymousUser.setAccessToken(userToken.getAccessToken());
        anonymousUser.setRefreshToken(userToken.getRefreshToken());
        log.info("Anonymous user created successfully");
        return anonymousUser;
    }

    @Override
    public String registerUser(User user) {
        log.info("Registering user method started");
        String token = getAdminToken();
        if (Boolean.TRUE.equals(enableEmailVerification)) {
            user.setPassword(null);
        }
        return createUser(adminRealm, token, user);
    }

    /**
     * Builds a new user representation from the request only (the current user is not read first), replaces the
     * realm roles when roles are given, and PUTs it to Keycloak.
     */
    @Override
    public User updateUser(User user) {
        log.info("Updating user method started");
        String token = getAdminToken();
        KeycloakDTOUser userRepresentation = new KeycloakDTOUser();
        userRepresentation.setId(user.getUserId());
        userRepresentation.setEmail(user.getEmail());
        updateName(user, userRepresentation);
        updateRole(adminRealm, user, token);
        updateAttributes(user, userRepresentation);
        setStatus(user, userRepresentation);
        try {
            HttpHandler.put(getUrl(adminRealm, URL_USER, user.getUserId()), token, userRepresentation);
            log.info("User updated successfully");
            return populateUser(userRepresentation, user);
        } catch (RestClientResponseException ex) {
            throwUserException(ex, user.getUserId());
        } catch (ResourceAccessException e) {
            throwResourceException(e);
        }
        return null;
    }

    @Override
    public void deleteUsers(List<String> userIds) {
        log.info("Deleting users method started");
        String token = getAdminToken();
        try {
            for (String userId : userIds) {
                HttpHandler.del(getUrl(adminRealm, URL_USER, userId), token, null);
                log.info("User deleted successfully");
            }
        } catch (RestClientResponseException ex) {
            throwUserException(ex, null);
        } catch (ResourceAccessException e) {
            throwResourceException(e);
        }
    }

    /** Empty in the original as well: anonymous users are never stored in Keycloak. */
    @Override
    public void retrieveAndDeleteAnonymousUser() {
        // nothing to do
    }

    @Override
    public UserInformation getUserInformation(String userId) {
        log.info("Get user information method started");
        String token = getAdminToken();
        KeycloakDTOUser userRepresentation = getUser(adminRealm, token, userId);
        List<KeycloakDTOCredentials> credentialList = getUserCredential(adminRealm, token, userId);
        UserInformation userInformation = new UserInformation();
        if (Boolean.TRUE.equals(userRepresentation.getEmailVerified())) {
            userInformation.setIsEmailVerified(true);
        }
        if (null != credentialList && !credentialList.isEmpty()) {
            userInformation.setIsPasswordSet(true);
        }
        log.info("User information retrieved successfully");
        return userInformation;
    }

    @Override
    public List<User> getUsers(String search) {
        log.info("Get users list retrieved started");
        String token = getAdminToken();
        List<KeycloakDTOUser> idpUsers = getUsers(adminRealm, token, search);
        List<User> users = new ArrayList<>();
        if (!idpUsers.isEmpty()) {
            for (KeycloakDTOUser idpUser : idpUsers) {
                users.add(setUserDetails(idpUser));
            }
        }
        log.info("Users list retrieved successfully");
        return users;
    }

    @Override
    public User getUserById(String userId) {
        log.info("Get users by id retrieved started");
        String token = getAdminToken();
        User user = setUserDetails(getUser(adminRealm, token, userId));
        log.info("Users by id retrieved successfully");
        return user;
    }

    @Override
    public void resendVerificationMail(String userId) {
        log.info("Resend verification mail method started");
        if (Boolean.FALSE.equals(enableEmailVerification)) {
            throw new InvalidConfigException(ErrorCodeConstants.MSG_SMPT_CONFIG, ErrorCodeConstants.MISSING_SMTP_CONFIG);
        }
        String token = getAdminToken();
        executeActionsEmail(adminRealm, userId, token);
    }

    /** PUTs a representation that only carries "enabled" (the current user is not read first). */
    @Override
    public Boolean changeUserStatus(String userId, String status) {
        log.info("Changing user status method started");
        String token = getAdminToken();
        try {
            var userRepresentation = new KeycloakDTOUser();
            var user = new User();
            user.setStatus(status);
            setStatus(user, userRepresentation);
            HttpHandler.put(getUrl(adminRealm, URL_USER, userId), token, userRepresentation);
            log.info("User status updated successfully");
            return true;
        } catch (RestClientResponseException ex) {
            throwUserException(ex, userId);
        } catch (ResourceAccessException e) {
            throwResourceException(e);
        }
        return false;
    }

    // ----------------------------------------------------------- user helpers

    private User setUserDetails(KeycloakDTOUser idpUser) {
        User user = new User();
        user.setUserId(idpUser.getId());
        user.setUserName(idpUser.getUsername());
        user.setEmail(idpUser.getEmail());
        user.setFirstName(idpUser.getFirstName());
        user.setLastName(idpUser.getLastName());
        user.setAdditionalAttributes(getIDPAttributes(idpUser));
        return user;
    }

    private Attribute[] getIDPAttributes(KeycloakDTOUser idpUser) {
        if (idpUser.getAttributes() == null) {
            return new Attribute[0];
        }
        Map<String, List<String>> attributes = idpUser.getAttributes();
        Attribute[] additionalAttributes = new Attribute[attributes.size()];
        var index = 0;
        for (Map.Entry<String, List<String>> entry : attributes.entrySet()) {
            var value = "";
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                value = entry.getValue().get(0);
            }
            additionalAttributes[index] = new Attribute(entry.getKey(), value);
            index++;
        }
        return additionalAttributes;
    }

    /** Validates the roles, creates the user, assigns the roles and (optionally) sends the setup email. */
    private String createUser(String userRealm, String token, User user) {
        List<KeycloakDTORoles> roleList = validateAndGetRoleReps(token, userRealm, user.getRoles());
        var userRepresentation = new KeycloakDTOUser();
        setStatus(user, userRepresentation);
        userRepresentation.setUsername(user.getUserName());
        userRepresentation.setFirstName(user.getFirstName());
        userRepresentation.setLastName(user.getLastName());
        userRepresentation.setEmail(user.getEmail());
        populateAttributeMap(user, userRepresentation);
        if (Objects.nonNull(user.getPassword()) && !user.getPassword().isEmpty()) {
            var passwordCred = new KeycloakDTOCredentials();
            passwordCred.setTemporary(isTemporary);
            passwordCred.setType(GRAND_TYPE_PASSWORD);
            passwordCred.setValue(user.getPassword());
            List<KeycloakDTOCredentials> credentials = new ArrayList<>();
            credentials.add(passwordCred);
            userRepresentation.setCredentials(credentials);
            userRepresentation.setEmailVerified(true);
        }
        userRepresentation = createUser(userRealm, token, userRepresentation);
        String userId = userRepresentation.getId();
        log.info("Assigning roles to user");
        assignRolesToUser(userRealm, userId, roleList, token);
        if (user.getPassword() == null && Boolean.TRUE.equals(enableEmailVerification)) {
            executeActionsEmail(userRealm, userId, token);
        }
        log.info("User created successfully");
        return userId;
    }

    /** POSTs the user, takes the id from the Location header and reads the created user back. */
    private KeycloakDTOUser createUser(String userRealm, String token, KeycloakDTOUser body) {
        log.info("Create user method started");
        String userId = "";
        try {
            String userUrl = HttpHandler.postWithLocationResponse(getUrl(userRealm, URL_USER), token, "Location", body);
            var splitVals = userUrl.split("/");
            userId = splitVals[splitVals.length - 1];
            log.info("User created successfully");
        } catch (RestClientResponseException e) {
            throwUserException(e, body.getUsername());
        } catch (ResourceAccessException e) {
            throwResourceException(e);
        }
        return getUser(userRealm, token, userId);
    }

    private List<KeycloakDTORoles> getUserRoles(String userRealm, String userId, String token) {
        log.info("Getting user roles");
        try {
            String url = getUrl(userRealm, URL_USER, userId) + "/role-mappings/realm";
            String response = HttpHandler.get(url, token, null);
            response = "{ \"roles\": " + response + " }";
            KeycloakDTOList roles = this.mapper.readValue(response, KeycloakDTOList.class);
            return roles.getRoles();
        } catch (RestClientResponseException e) {
            throwUserException(e, userId);
        } catch (ResourceAccessException e) {
            throwResourceException(e);
        } catch (JacksonException e) {
            log.error(RUNTIME_EXCEPTION_MESSAGE, e.getMessage());
            throwUnhandledException();
        }
        return new ArrayList<>();
    }

    private void assignRolesToUser(String userRealm, String userId, List<KeycloakDTORoles> roles, String token) {
        log.info("Assigning roles to user");
        try {
            String url = getUrl(userRealm, URL_USER, userId) + "/role-mappings/realm";
            HttpHandler.post(url, token, roles);
            log.info("Roles assigned to user successfully");
        } catch (RestClientResponseException e) {
            throwUserException(e, userId);
        } catch (ResourceAccessException e) {
            throwResourceException(e);
        }
    }

    private void removeRolesFromUser(String userRealm, String userId, List<KeycloakDTORoles> roles, String token) {
        log.info("Removing roles from user");
        try {
            String url = getUrl(userRealm, URL_USER, userId) + "/role-mappings/realm";
            HttpHandler.del(url, token, roles);
            log.info("Roles removed from user successfully");
        } catch (RestClientResponseException e) {
            throwUserException(e, userId);
        } catch (ResourceAccessException e) {
            throwResourceException(e);
        }
    }

    /** Keycloak "execute actions" email: VERIFY_EMAIL + UPDATE_PASSWORD (needs SMTP in the realm). */
    private void executeActionsEmail(String userRealm, String userId, String token) {
        log.info("Sending email verification");
        try {
            String queryPrams = "client_id=" + adminClientId + "&lifespan=" + emailLifespan + "&redirect_uri=" + ssoRedirectUrl;
            String url = getUrl(userRealm, URL_USER, userId) + "/execute-actions-email?" + queryPrams;
            HttpHandler.put(url, token, List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));
            log.info("Email verification sent successfully");
        } catch (RestClientResponseException e) {
            throwUserException(e, userId);
        } catch (ResourceAccessException e) {
            throwResourceException(e);
        }
    }

    /** Loads all realm roles and maps each requested name to its representation; unknown name = Role not found. */
    private List<KeycloakDTORoles> validateAndGetRoleReps(String token, String userRealm, List<String> roles) {
        log.info("Validating and getting role representations");
        List<KeycloakDTORoles> roleList = new ArrayList<>();
        List<KeycloakDTORoles> keycloakRoles = getRoles(userRealm, token);
        for (String role : roles) {
            KeycloakDTORoles keycloakRole = keycloakRoles.stream().filter(r -> r.getName().equals(role)).findFirst().orElse(null);
            if (keycloakRole == null) {
                throw new NotFoundException(ErrorCodeConstants.MSG_ROLE_NOT_FOUND, ErrorCodeConstants.ROLE_NOT_FOUND, role);
            }
            roleList.add(keycloakRole);
        }
        log.info("Role representations validated and retrieved successfully");
        return roleList;
    }

    /** ENABLE -> enabled, DISABLE -> disabled, empty/missing -> enabled. */
    private static void setStatus(User user, KeycloakDTOUser userRepresentation) {
        log.info("Setting user status");
        if (user.getStatus() != null && !"".equals(user.getStatus())) {
            if (user.getStatus().equalsIgnoreCase(KeycloakConstants.USER_STATUS_ENABLE)) {
                userRepresentation.setEnabled(true);
            } else if (user.getStatus().equalsIgnoreCase(KeycloakConstants.USER_STATUS_DISABLE)) {
                userRepresentation.setEnabled(false);
            }
        } else {
            userRepresentation.setEnabled(true);
        }
        log.info("User status set successfully");
    }

    private static void populateAttributeMap(User user, KeycloakDTOUser userRepresentation) {
        log.info("Adding additional attributes including entity_type and entity_id");
        HashMap<String, List<String>> attributeMap = new HashMap<>();
        if (user.getEntityType() != null && !user.getEntityType().isEmpty()) {
            attributeMap.put(KeycloakConstants.ATTR_ENTITY_TYPE, Collections.singletonList(user.getEntityType()));
        }
        if (user.getEntityId() != null && !user.getEntityId().isEmpty()) {
            attributeMap.put(KeycloakConstants.ATTR_ENTITY_ID, Collections.singletonList(user.getEntityId()));
        }
        Attribute[] additionalAttributes = user.getAdditionalAttributes();
        if (additionalAttributes != null) {
            for (Attribute attr : additionalAttributes) {
                attributeMap.put(attr.getKey(), Collections.singletonList(attr.getValue()));
            }
        }
        if (!attributeMap.isEmpty()) {
            userRepresentation.setAttributes(attributeMap);
        }
        log.info("Additional attributes added successfully");
    }

    private static User populateUser(KeycloakDTOUser userRepresentation, User user) {
        log.info("Populating user object");
        if (userRepresentation != null) {
            user.setFirstName(userRepresentation.getFirstName());
            user.setLastName(userRepresentation.getLastName());
            user.setPassword(null);
            if (Boolean.TRUE.equals(userRepresentation.getEnabled())) {
                user.setStatus(KeycloakConstants.USER_STATUS_ENABLE);
            } else {
                user.setStatus(KeycloakConstants.USER_STATUS_DISABLE);
            }
            populateUserAttributes(userRepresentation, user);
        }
        log.info("User object populated successfully");
        return user;
    }

    private static void populateUserAttributes(KeycloakDTOUser userRepresentation, User user) {
        log.info("Populating user additional attributes");
        Map<String, List<String>> userAttributes = userRepresentation.getAttributes();
        if (userAttributes != null && !userAttributes.isEmpty()) {
            if (userAttributes.get(KeycloakConstants.ATTR_ENTITY_ID) != null && !userAttributes.get(KeycloakConstants.ATTR_ENTITY_ID).isEmpty()) {
                user.setEntityId(String.join(", ", userAttributes.get(KeycloakConstants.ATTR_ENTITY_ID)));
                userAttributes.remove(KeycloakConstants.ATTR_ENTITY_ID);
            }
            if (userAttributes.get(KeycloakConstants.ATTR_ENTITY_TYPE) != null && !userAttributes.get(KeycloakConstants.ATTR_ENTITY_TYPE).isEmpty()) {
                user.setEntityType(String.join(", ", userAttributes.get(KeycloakConstants.ATTR_ENTITY_TYPE)));
                userAttributes.remove(KeycloakConstants.ATTR_ENTITY_TYPE);
            }
            var attributes = new Attribute[userAttributes.size()];
            var count = 0;
            for (Map.Entry<String, List<String>> entry : userAttributes.entrySet()) {
                attributes[count] = new Attribute(entry.getKey(), String.join(", ", entry.getValue()));
                count++;
            }
            user.setAdditionalAttributes(attributes);
        }
        log.info("User additional attributes populated successfully");
    }

    private static void updateName(User user, KeycloakDTOUser userRepresentation) {
        log.info("Updating user first name and last name");
        if (user.getFirstName() == null || "".equals(user.getFirstName())) {
            userRepresentation.setFirstName(userRepresentation.getFirstName());
        } else {
            userRepresentation.setFirstName(user.getFirstName());
        }
        if (user.getLastName() == null || "".equals(user.getLastName())) {
            userRepresentation.setLastName(userRepresentation.getLastName());
        } else {
            userRepresentation.setLastName(user.getLastName());
        }
        log.info("User first name and last name updated successfully");
    }

    /** When roles is present: removes all current realm roles of the user, then assigns the requested ones. */
    private void updateRole(String userRealm, User user, String token) {
        log.info("Updating user roles");
        List<KeycloakDTORoles> oldRoles = new ArrayList<>();
        List<KeycloakDTORoles> newRoles = new ArrayList<>();
        if (user.getRoles() != null) {
            oldRoles = getUserRoles(userRealm, user.getUserId(), token);
            if (!user.getRoles().isEmpty()) {
                newRoles = validateAndGetRoleReps(token, userRealm, user.getRoles());
            }
        }
        if (!oldRoles.isEmpty()) {
            removeRolesFromUser(userRealm, user.getUserId(), oldRoles, token);
        }
        if (!newRoles.isEmpty()) {
            assignRolesToUser(userRealm, user.getUserId(), newRoles, token);
        }
        log.info("User roles updated successfully");
    }

    /** When attributes/entityId/entityType are present the user's attributes are replaced by exactly these. */
    private static void updateAttributes(User user, KeycloakDTOUser userRepresentation) {
        log.info("Updating user additional attributes");
        Map<String, List<String>> attributeMap = new HashMap<>();
        Attribute[] additionalAttributes = user.getAdditionalAttributes();
        if (additionalAttributes != null
                || (user.getEntityId() != null && !user.getEntityId().isEmpty())
                || (user.getEntityType() != null && !user.getEntityType().isEmpty())) {
            if (additionalAttributes != null) {
                for (Attribute attr : additionalAttributes) {
                    attributeMap.put(attr.getKey(), Collections.singletonList(attr.getValue()));
                }
            }
            updateEntityTypeAndIdAttributes(user, attributeMap);
            userRepresentation.setAttributes(attributeMap);
        }
        log.info("User additional attributes updated successfully");
    }

    /**
     * Kept exactly as in bm-usermanagement-v4: entityType is written under the entity_id key (the original
     * code does this, so an update with entityType overwrites entity_id and does not set entity_type).
     */
    private static void updateEntityTypeAndIdAttributes(User user, Map<String, List<String>> attributeMap) {
        log.info("Updating entity type and entity id attributes");
        if ((user.getEntityId() != null && !user.getEntityId().isEmpty()) || (user.getEntityType() != null && !user.getEntityType().isEmpty())) {
            if (user.getEntityId() != null && !user.getEntityId().isEmpty()) {
                attributeMap.put(KeycloakConstants.ATTR_ENTITY_ID, Collections.singletonList(user.getEntityId()));
            }
            if (user.getEntityType() != null && !user.getEntityType().isEmpty()) {
                attributeMap.put(KeycloakConstants.ATTR_ENTITY_ID, Collections.singletonList(user.getEntityType()));
            }
        }
        log.info("Entity type and entity id attributes updated successfully");
    }

    // ------------------------------------------------------- Keycloak reads

    private KeycloakDTOUser getUser(String userRealm, String token, String userId) {
        log.info("Getting user details");
        try {
            String url = getUrl(userRealm, URL_USER) + "/" + userId;
            String response = HttpHandler.get(url, token, null);
            KeycloakDTOUser user = this.mapper.readValue(response, KeycloakDTOUser.class);
            log.info("User details retrieved successfully");
            return user;
        } catch (RestClientResponseException e) {
            throwUserException(e, null);
        } catch (ResourceAccessException e) {
            throwResourceException(e);
        } catch (JacksonException e) {
            log.error(RUNTIME_EXCEPTION_MESSAGE, e.getMessage());
            throwUnhandledException();
        }
        return null;
    }

    private List<KeycloakDTOUser> getUsers(String userRealm, String token, String search) {
        log.info("Getting user details");
        try {
            String response = HttpHandler.userSearch(getUrl(userRealm, URL_USER), search, token);
            List<KeycloakDTOUser> users = this.mapper.readValue(response, new TypeReference<List<KeycloakDTOUser>>() { });
            log.info("User details retrieved successfully");
            return users;
        } catch (RestClientResponseException e) {
            throwUserException(e, null);
        } catch (ResourceAccessException e) {
            throwResourceException(e);
        } catch (JacksonException e) {
            log.error(RUNTIME_EXCEPTION_MESSAGE, e.getMessage());
            throwUnhandledException();
        }
        return new ArrayList<>();
    }

    private List<KeycloakDTOCredentials> getUserCredential(String userRealm, String token, String userId) {
        log.info("Getting user credentials");
        try {
            String url = getUrl(userRealm, URL_USER) + "/" + userId + "/credentials";
            String response = HttpHandler.get(url, token, null);
            log.info("User credentials retrieved successfully");
            return this.mapper.readValue(response, new TypeReference<List<KeycloakDTOCredentials>>() { });
        } catch (RestClientResponseException e) {
            throwUserException(e, null);
        } catch (ResourceAccessException e) {
            throwResourceException(e);
        } catch (JacksonException e) {
            log.error(RUNTIME_EXCEPTION_MESSAGE, e.getMessage());
            throwUnhandledException();
        }
        return new ArrayList<>();
    }

    private List<KeycloakDTORoles> getRoles(String userRealm, String token) {
        log.info("Getting roles");
        try {
            String url = getUrl(userRealm, URL_ROLES);
            String response = HttpHandler.get(url, token, null);
            List<KeycloakDTORoles> roles = this.mapper.readValue(response, new TypeReference<List<KeycloakDTORoles>>() { });
            log.info("Roles retrieved successfully");
            return roles;
        } catch (RestClientResponseException e) {
            throwRoleException(e, null);
        } catch (ResourceAccessException e) {
            throwResourceException(e);
        } catch (JacksonException e) {
            log.error(RUNTIME_EXCEPTION_MESSAGE, e.getMessage());
            throwUnhandledException();
        }
        return new ArrayList<>();
    }

    private KeycloakDTORoles getRole(String userRealm, String token, String role) {
        log.info("Getting roles");
        try {
            String url = getUrl(userRealm, URL_ROLES, role);
            String response = HttpHandler.get(url, token, null);
            KeycloakDTORoles roleObj = this.mapper.readValue(response, KeycloakDTORoles.class);
            log.info("Roles retrieved successfully");
            return roleObj;
        } catch (RestClientResponseException e) {
            throwRoleException(e, null);
        } catch (ResourceAccessException e) {
            throwResourceException(e);
        } catch (JacksonException e) {
            log.error(RUNTIME_EXCEPTION_MESSAGE, e.getMessage());
            throwUnhandledException();
        }
        return null;
    }

    private List<KeycloakDTORoles> getCompositeRoles(String userRealm, String token, String role) {
        log.info("Getting composite roles started");
        try {
            String url = getUrl(userRealm, URL_ROLES, role) + "/composites";
            String response = HttpHandler.get(url, token, null);
            List<KeycloakDTORoles> roles = this.mapper.readValue(response, new TypeReference<List<KeycloakDTORoles>>() { });
            log.info("Roles retrieved successfully");
            return roles;
        } catch (RestClientResponseException e) {
            throwRoleException(e, null);
        } catch (ResourceAccessException e) {
            throwResourceException(e);
        } catch (JacksonException e) {
            log.error(RUNTIME_EXCEPTION_MESSAGE, e.getMessage());
            throwUnhandledException();
        }
        return new ArrayList<>();
    }

    // ------------------------------------------------------ error translation

    private void throwUserException(RestClientResponseException e, String userName) {
        log.error("RestClientResponseException occurred with message: {}", e.getMessage());
        if (e.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
            throw new KeycloakAdminClientException(ErrorCodeConstants.MSG_INVALID_CREDENTIALS,
                    UMErrorCodeConstants.ADMIN_TOKEN_REJECTED, adminClientId);
        } else if (e.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
            throw new KeycloakAdminClientException(ErrorCodeConstants.MSG_UNAUTHORIZED_KEYCLOAK,
                    UMErrorCodeConstants.ADMIN_CLIENT_FORBIDDEN, adminClientId);
        } else if (e.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()) {
            throw new InvalidClientException(e.getMessage(), ErrorCodeConstants.INVALID_CONFIG);
        } else if (e.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
            throw new UserAlreadyExistsException(ErrorCodeConstants.MSG_USER_EXISTS, ErrorCodeConstants.USER_EXISTS, userName);
        } else if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
            throw new NotFoundException(ErrorCodeConstants.MSG_USER_NOT_FOUND, ErrorCodeConstants.USER_NOT_FOUND, userName);
        } else {
            throwUnhandledException();
        }
    }

    private void throwRoleException(RestClientResponseException e, String roleName) {
        log.error("RestClientResponseException occurred with message: {}", e.getMessage());
        if (e.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
            throw new KeycloakAdminClientException(ErrorCodeConstants.MSG_INVALID_CREDENTIALS,
                    UMErrorCodeConstants.ADMIN_TOKEN_REJECTED, adminClientId);
        } else if (e.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
            throw new KeycloakAdminClientException(ErrorCodeConstants.MSG_UNAUTHORIZED_KEYCLOAK,
                    UMErrorCodeConstants.ADMIN_CLIENT_FORBIDDEN, adminClientId);
        } else if (e.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()) {
            throw new InvalidClientException(ErrorCodeConstants.MSG_INVALID_CONFIG, ErrorCodeConstants.INVALID_CONFIG);
        } else if (e.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
            throw new RoleAlreadyExistsException(ErrorCodeConstants.MSG_ROLE_EXISTS, ErrorCodeConstants.ROLE_EXISTS, roleName);
        } else if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
            throw new NotFoundException(ErrorCodeConstants.MSG_ROLE_NOT_FOUND, ErrorCodeConstants.ROLE_NOT_FOUND, roleName);
        } else {
            throwUnhandledException();
        }
    }

    private void throwResourceException(ResourceAccessException e) {
        log.error("ResourceAccessException occurred with message: {}", e.getMessage());
        if (e.getCause() != null && e.getCause().getClass() == SSLHandshakeException.class) {
            throw new ConnectionTimeOutException(ErrorCodeConstants.MSG_CONNECTION_TIMEOUT, ErrorCodeConstants.CONNECTION_TIMEOUT);
        } else {
            throwUnhandledException();
        }
    }

    private static void throwUnhandledException() {
        throw new UnhandledException(ErrorCodeConstants.MSG_UNHANDLED, ErrorCodeConstants.UNHANDLED);
    }

    // ------------------------------------------------------------ token/url

    /** A new admin token (client credentials of the admin client) is requested for every operation. */
    private String getAdminToken() {
        log.info("Get api admin token started");
        String body = getTokenBody(adminClientId, adminClientSecret, GRAND_TYPE_CLIENT_CREDENTIAL, null, null, null, null);
        try {
            return getToken(adminRealm, body).getAccessToken();
        } catch (InvalidCredentialsException e) {
            // Keycloak refused the service's own admin client: a configuration problem, not the caller's fault
            log.error("Keycloak rejected the admin client '{}': check client_id/client_secret in application.yaml", adminClientId);
            throw new KeycloakAdminClientException(e.getMessage(), UMErrorCodeConstants.ADMIN_CLIENT_LOGIN_FAILED, adminClientId);
        }
    }

    private Token getToken(String realm, String body) {
        log.info("Get token api started");
        try {
            String url = getUrl(realm, URL_TOKEN);
            String result = HttpHandler.getToken(url, body);
            KeycloakDTOToken response = this.mapper.readValue(result, KeycloakDTOToken.class);
            var token = new Token();
            token.setAccessToken(response.getAccess_token());
            token.setRefreshToken(response.getRefresh_token());
            log.info("Api token retrieved successfully");
            return token;
        } catch (RestClientResponseException e) {
            log.error("HttpResponseException occurred with message: {}", e.getMessage());
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new NotFoundException(ErrorCodeConstants.MSG_REALM_NOT_FOUND, ErrorCodeConstants.REALM_NOT_FOUND, realm);
            } else if (e.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()) {
                throw new InvalidClientException(ErrorCodeConstants.MSG_INVALID_CONFIG, ErrorCodeConstants.INVALID_CONFIG);
            } else if (e.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                throw new InvalidCredentialsException(ErrorCodeConstants.MSG_INVALID_CREDENTIALS, ErrorCodeConstants.INVALID_CREDENTIALS);
            } else {
                throw new UnhandledException(ErrorCodeConstants.MSG_UNHANDLED, ErrorCodeConstants.UNHANDLED);
            }
        } catch (JacksonException ex) {
            throw new UnhandledException(ErrorCodeConstants.MSG_UNHANDLED, ErrorCodeConstants.UNHANDLED);
        } catch (ResourceAccessException e) {
            throwResourceException(e);
        }
        return null;
    }

    private String getUrl(String realm, String url) {
        return serverUrl + url.replace("{{realm}}", realm);
    }

    private String getUrl(String realm, String url, String urlParam) {
        return getUrl(realm, url) + "/" + urlParam;
    }

    private String getTokenBody(String clientId, String clientSrt, String grantType,
            String scope, String userName, String userPWD, String refreshToken) {
        log.info("Get token body started");
        String result = "";
        if (clientSrt != null) {
            result += CLIENT_SECRET_KEY + "=" + clientSrt + "&";
        }
        if (scope != null) {
            result += SCOPE_KEY + "=" + scope + "&";
        }
        if (userName != null) {
            result += USERNAME_KEY + "=" + userName + "&";
        }
        if (userPWD != null) {
            result += PASSWORD_KEY + "=" + userPWD + "&";
        }
        if (refreshToken != null) {
            result += REFRESH_TOKEN_KEY + "=" + refreshToken + "&";
        }
        result += CLIENT_ID_KEY + "=" + clientId + "&";
        result += GRAND_TYPE_KEY + "=" + grantType;
        log.info("Token body generated successfully");
        return result;
    }
}

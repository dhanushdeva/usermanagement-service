package com.usermanagement.identityprovider.keycloak;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.usermanagement.config.KeycloakProperties;
import com.usermanagement.constants.UserStatus;
import com.usermanagement.dto.AttributeDto;
import com.usermanagement.dto.RoleDto;
import com.usermanagement.dto.UserDto;
import com.usermanagement.entity.AnonymousUserResponse;
import com.usermanagement.exception.ErrorCode;
import com.usermanagement.exception.UserManagementException;
import com.usermanagement.identityprovider.IdentityProvider;
import com.usermanagement.entity.KeycloakCredential;
import com.usermanagement.entity.KeycloakRole;
import com.usermanagement.entity.KeycloakTokenResponse;
import com.usermanagement.entity.KeycloakUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keycloak implementation: translates service DTOs to Keycloak representations and orchestrates Admin API calls.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakIdentityProvider implements IdentityProvider {

    static final String ATTRIBUTE_ENTITY_TYPE = "entity_type";
    static final String ATTRIBUTE_ENTITY_ID = "entity_id";
    /** Keycloak's built-in composite role every user gets; never removed when roles are replaced. */
    private static final String DEFAULT_ROLES_PREFIX = "default-roles-";
    private static final List<String> ACCOUNT_SETUP_ACTIONS = List.of("VERIFY_EMAIL", "UPDATE_PASSWORD");

    private final KeycloakAdminClient keycloakAdminClient;
    private final KeycloakProperties keycloakProperties;

    // ------------------------------------------------------------------ users

    /** Anonymous users only get a token of the anonymous client; nothing is stored in Keycloak. */
    @Override
    public AnonymousUserResponse createAnonymousUser() {
        KeycloakTokenResponse token = keycloakAdminClient.requestClientCredentialsToken(keycloakProperties.anonymousClient());
        return new AnonymousUserResponse(null, UUID.randomUUID().toString(), token.accessToken(), token.refreshToken());
    }

    @Override
    public String registerUser(UserDto user) {
        boolean sendAccountSetupEmail = keycloakProperties.emailVerification().enabled();
        List<KeycloakRole> realmRoles = findRealmRoles(user.getRoles());

        KeycloakUser keycloakUser = new KeycloakUser();
        keycloakUser.setUsername(user.getUserName());
        keycloakUser.setFirstName(user.getFirstName());
        keycloakUser.setLastName(user.getLastName());
        keycloakUser.setEmail(user.getEmail());
        keycloakUser.setEnabled(toEnabledFlag(user.getStatus()).orElse(true));

        Map<String, List<String>> attributes = mergeAttributes(new HashMap<>(), user);
        keycloakUser.setAttributes(attributes.isEmpty() ? null : attributes);

        // With email verification the user sets the password through the email link instead
        if (!sendAccountSetupEmail && StringUtils.hasText(user.getPassword())) {
            keycloakUser.setCredentials(List.of(
                    KeycloakCredential.password(user.getPassword(), keycloakProperties.temporaryPassword())));
            keycloakUser.setEmailVerified(true);
        }

        String userId = keycloakAdminClient.createUser(keycloakUser);
        if (!realmRoles.isEmpty()) {
            keycloakAdminClient.addRealmRolesToUser(userId, realmRoles);
        }
        if (sendAccountSetupEmail) {
            sendAccountSetupEmail(userId);
        }
        log.info("Registered user '{}' with id {}", user.getUserName(), userId);
        return userId;
    }

    /**
     * Updates only the fields present in the request. Attributes are merged into the existing ones.
     * When {@code roles} is given, the user's realm roles are replaced by exactly that list.
     */
    @Override
    public UserDto updateUser(UserDto user) {
        String userId = user.getUserId();
        List<KeycloakRole> requestedRoles = user.getRoles() == null ? null : findRealmRoles(user.getRoles());

        KeycloakUser keycloakUser = keycloakAdminClient.getUser(userId);
        if (StringUtils.hasText(user.getEmail())) {
            keycloakUser.setEmail(user.getEmail());
        }
        if (StringUtils.hasText(user.getFirstName())) {
            keycloakUser.setFirstName(user.getFirstName());
        }
        if (StringUtils.hasText(user.getLastName())) {
            keycloakUser.setLastName(user.getLastName());
        }
        toEnabledFlag(user.getStatus()).ifPresent(keycloakUser::setEnabled);
        if (hasAttributeChanges(user)) {
            Map<String, List<String>> existing = keycloakUser.getAttributes();
            keycloakUser.setAttributes(mergeAttributes(existing == null ? new HashMap<>() : new HashMap<>(existing), user));
        }

        keycloakAdminClient.updateUser(userId, keycloakUser);
        if (requestedRoles != null) {
            replaceRealmRoles(userId, requestedRoles);
        }

        UserDto updatedUser = toUserDto(keycloakUser);
        if (requestedRoles != null) {
            updatedUser.setRoles(requestedRoles.stream().map(KeycloakRole::getName).toList());
        }
        log.info("Updated user {}", userId);
        return updatedUser;
    }

    @Override
    public void deleteUsers(List<String> userIds) {
        userIds.forEach(userId -> {
            keycloakAdminClient.deleteUser(userId);
            log.info("Deleted user {}", userId);
        });
    }

    @Override
    public void changeUserStatus(String userId, UserStatus status) {
        KeycloakUser keycloakUser = keycloakAdminClient.getUser(userId);
        keycloakUser.setEnabled(status.isEnabled());
        keycloakAdminClient.updateUser(userId, keycloakUser);
        log.info("User {} is now {}", userId, status);
    }

    // ------------------------------------------------------------------ roles

    @Override
    public String createRole(RoleDto role) {
        keycloakAdminClient.createRealmRole(KeycloakRole.newRealmRole(role.name(), role.description()));
        log.info("Created realm role '{}'", role.name());
        return role.name();
    }

    @Override
    public void deleteRole(String roleName) {
        keycloakAdminClient.deleteRealmRole(roleName);
        log.info("Deleted realm role '{}'", roleName);
    }

    // ---------------------------------------------------------------- helpers

    /** Loads each role from Keycloak; fails with ROLE_NOT_FOUND before anything is changed. */
    private List<KeycloakRole> findRealmRoles(List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return List.of();
        }
        return roleNames.stream().distinct().map(keycloakAdminClient::getRealmRole).toList();
    }

    private void replaceRealmRoles(String userId, List<KeycloakRole> requestedRoles) {
        List<KeycloakRole> currentRoles = keycloakAdminClient.getUserRealmRoles(userId);
        Set<String> requestedNames = requestedRoles.stream().map(KeycloakRole::getName).collect(Collectors.toSet());
        Set<String> currentNames = currentRoles.stream().map(KeycloakRole::getName).collect(Collectors.toSet());

        List<KeycloakRole> rolesToRemove = currentRoles.stream()
                .filter(role -> !requestedNames.contains(role.getName()))
                .filter(role -> !role.getName().startsWith(DEFAULT_ROLES_PREFIX))
                .toList();
        List<KeycloakRole> rolesToAdd = requestedRoles.stream()
                .filter(role -> !currentNames.contains(role.getName()))
                .toList();

        if (!rolesToRemove.isEmpty()) {
            keycloakAdminClient.removeRealmRolesFromUser(userId, rolesToRemove);
        }
        if (!rolesToAdd.isEmpty()) {
            keycloakAdminClient.addRealmRolesToUser(userId, rolesToAdd);
        }
    }

    private void sendAccountSetupEmail(String userId) {
        KeycloakProperties.EmailVerification settings = keycloakProperties.emailVerification();
        keycloakAdminClient.sendActionsEmail(userId, ACCOUNT_SETUP_ACTIONS, keycloakProperties.adminClient().clientId(),
                settings.redirectUri(), settings.linkLifespan().toSeconds());
        log.info("Account setup email sent to user {}", userId);
    }

    private static Optional<Boolean> toEnabledFlag(String status) {
        if (!StringUtils.hasText(status)) {
            return Optional.empty();
        }
        UserStatus userStatus = UserStatus.fromValue(status)
                .orElseThrow(() -> new UserManagementException(ErrorCode.INVALID_USER_STATUS, status));
        return Optional.of(userStatus.isEnabled());
    }

    private static boolean hasAttributeChanges(UserDto user) {
        return user.getAdditionalAttributes() != null
                || StringUtils.hasText(user.getEntityType())
                || StringUtils.hasText(user.getEntityId());
    }

    /** Adds additionalAttributes, then entityType/entityId (stored as entity_type / entity_id). */
    private static Map<String, List<String>> mergeAttributes(Map<String, List<String>> attributes, UserDto user) {
        if (user.getAdditionalAttributes() != null) {
            user.getAdditionalAttributes().stream()
                    .filter(attribute -> StringUtils.hasText(attribute.key()))
                    .forEach(attribute -> attributes.put(attribute.key(),
                            attribute.value() == null ? List.of() : List.of(attribute.value())));
        }
        if (StringUtils.hasText(user.getEntityType())) {
            attributes.put(ATTRIBUTE_ENTITY_TYPE, List.of(user.getEntityType()));
        }
        if (StringUtils.hasText(user.getEntityId())) {
            attributes.put(ATTRIBUTE_ENTITY_ID, List.of(user.getEntityId()));
        }
        return attributes;
    }

    private static UserDto toUserDto(KeycloakUser keycloakUser) {
        UserDto user = new UserDto();
        user.setUserId(keycloakUser.getId());
        user.setUserName(keycloakUser.getUsername());
        user.setFirstName(keycloakUser.getFirstName());
        user.setLastName(keycloakUser.getLastName());
        user.setEmail(keycloakUser.getEmail());
        user.setStatus(UserStatus.fromEnabled(keycloakUser.getEnabled()).name());

        List<AttributeDto> additionalAttributes = new ArrayList<>();
        if (keycloakUser.getAttributes() != null) {
            keycloakUser.getAttributes().forEach((key, values) -> {
                String value = values == null ? "" : String.join(", ", values);
                switch (key) {
                    case ATTRIBUTE_ENTITY_TYPE -> user.setEntityType(value);
                    case ATTRIBUTE_ENTITY_ID -> user.setEntityId(value);
                    default -> additionalAttributes.add(new AttributeDto(key, value));
                }
            });
        }
        user.setAdditionalAttributes(additionalAttributes);
        return user;
    }
}

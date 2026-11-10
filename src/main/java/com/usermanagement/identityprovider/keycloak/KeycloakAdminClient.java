package com.usermanagement.identityprovider.keycloak;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.usermanagement.config.KeycloakProperties;
import com.usermanagement.config.KeycloakProperties.ClientCredentials;
import com.usermanagement.exception.ErrorCode;
import com.usermanagement.exception.UserManagementException;
import com.usermanagement.identityprovider.keycloak.KeycloakErrorTranslator.KeycloakResource;
import com.usermanagement.entity.KeycloakCredential;
import com.usermanagement.entity.KeycloakRole;
import com.usermanagement.entity.KeycloakTokenResponse;
import com.usermanagement.entity.KeycloakUser;

import lombok.extern.slf4j.Slf4j;

/**
 * Low-level HTTP calls to Keycloak (token endpoint + Admin REST API). No business logic here.
 * Admin calls use a token of the admin client (client_credentials grant), cached until shortly before it expires.
 */
@Slf4j
@Component
public class KeycloakAdminClient {

    private static final String TOKEN_PATH = "/realms/{realm}/protocol/openid-connect/token";
    private static final String USERS_PATH = "/admin/realms/{realm}/users";
    private static final String USER_PATH = USERS_PATH + "/{userId}";
    private static final String USER_REALM_ROLES_PATH = USER_PATH + "/role-mappings/realm";
    private static final String USER_CREDENTIALS_PATH = USER_PATH + "/credentials";
    private static final String USER_ACTIONS_EMAIL_PATH = USER_PATH + "/execute-actions-email";
    private static final String ROLES_PATH = "/admin/realms/{realm}/roles";
    private static final String ROLE_PATH = ROLES_PATH + "/{roleName}";
    private static final String ROLE_COMPOSITES_PATH = ROLE_PATH + "/composites";

    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofSeconds(30);
    private static final ParameterizedTypeReference<List<KeycloakUser>> USER_LIST = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<KeycloakRole>> ROLE_LIST = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<KeycloakCredential>> CREDENTIAL_LIST = new ParameterizedTypeReference<>() { };

    private final RestClient keycloakRestClient;
    private final KeycloakProperties keycloakProperties;
    private final String realm;

    private CachedToken cachedAdminToken;

    public KeycloakAdminClient(RestClient keycloakRestClient, KeycloakProperties keycloakProperties) {
        this.keycloakRestClient = keycloakRestClient;
        this.keycloakProperties = keycloakProperties;
        this.realm = keycloakProperties.realm();
    }

    // ----------------------------------------------------------------- tokens

    public KeycloakTokenResponse requestClientCredentialsToken(ClientCredentials client) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", client.clientId());
        form.add("client_secret", client.clientSecret());

        return execute(KeycloakResource.REALM, realm, () -> keycloakRestClient.post()
                .uri(TOKEN_PATH, realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KeycloakTokenResponse.class));
    }

    synchronized String getAdminAccessToken() {
        if (cachedAdminToken == null || cachedAdminToken.isAboutToExpire()) {
            log.debug("Requesting a new admin token for client '{}'", keycloakProperties.adminClient().clientId());
            KeycloakTokenResponse token = requestClientCredentialsToken(keycloakProperties.adminClient());
            cachedAdminToken = new CachedToken(token.accessToken(),
                    Instant.now().plusSeconds(token.expiresInSeconds()).minus(TOKEN_REFRESH_MARGIN));
        }
        return cachedAdminToken.value();
    }

    private synchronized void forgetAdminToken() {
        cachedAdminToken = null;
    }

    // ------------------------------------------------------------------ users

    /** Creates the user and returns the id Keycloak generated (taken from the Location header). */
    public String createUser(KeycloakUser user) {
        URI location = execute(KeycloakResource.USER, user.getUsername(), () -> keycloakRestClient.post()
                .uri(USERS_PATH, realm)
                .headers(adminAuthorization())
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .toBodilessEntity()
                .getHeaders()
                .getLocation());
        if (location == null) {
            throw new UserManagementException(ErrorCode.UNEXPECTED_ERROR, user.getUsername());
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public KeycloakUser getUser(String userId) {
        return execute(KeycloakResource.USER, userId, () -> keycloakRestClient.get()
                .uri(USER_PATH, realm, userId)
                .headers(adminAuthorization())
                .retrieve()
                .body(KeycloakUser.class));
    }

    /** Keycloak searches username, first/last name and email. */
    public List<KeycloakUser> searchUsers(String search) {
        List<KeycloakUser> users = execute(KeycloakResource.USER, search, () -> keycloakRestClient.get()
                .uri(builder -> builder.path(USERS_PATH).queryParam("search", "{search}").build(realm, search))
                .headers(adminAuthorization())
                .retrieve()
                .body(USER_LIST));
        return users != null ? users : List.of();
    }

    public void updateUser(String userId, KeycloakUser user) {
        execute(KeycloakResource.USER, userId, () -> keycloakRestClient.put()
                .uri(USER_PATH, realm, userId)
                .headers(adminAuthorization())
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .toBodilessEntity());
    }

    public void deleteUser(String userId) {
        execute(KeycloakResource.USER, userId, () -> keycloakRestClient.delete()
                .uri(USER_PATH, realm, userId)
                .headers(adminAuthorization())
                .retrieve()
                .toBodilessEntity());
    }

    public List<KeycloakCredential> getUserCredentials(String userId) {
        List<KeycloakCredential> credentials = execute(KeycloakResource.USER, userId, () -> keycloakRestClient.get()
                .uri(USER_CREDENTIALS_PATH, realm, userId)
                .headers(adminAuthorization())
                .retrieve()
                .body(CREDENTIAL_LIST));
        return credentials != null ? credentials : List.of();
    }

    public List<KeycloakRole> getUserRealmRoles(String userId) {
        List<KeycloakRole> roles = execute(KeycloakResource.USER, userId, () -> keycloakRestClient.get()
                .uri(USER_REALM_ROLES_PATH, realm, userId)
                .headers(adminAuthorization())
                .retrieve()
                .body(ROLE_LIST));
        return roles != null ? roles : List.of();
    }

    public void addRealmRolesToUser(String userId, List<KeycloakRole> roles) {
        execute(KeycloakResource.USER, userId, () -> keycloakRestClient.post()
                .uri(USER_REALM_ROLES_PATH, realm, userId)
                .headers(adminAuthorization())
                .contentType(MediaType.APPLICATION_JSON)
                .body(roles)
                .retrieve()
                .toBodilessEntity());
    }

    public void removeRealmRolesFromUser(String userId, List<KeycloakRole> roles) {
        execute(KeycloakResource.USER, userId, () -> keycloakRestClient.method(HttpMethod.DELETE)
                .uri(USER_REALM_ROLES_PATH, realm, userId)
                .headers(adminAuthorization())
                .contentType(MediaType.APPLICATION_JSON)
                .body(roles)
                .retrieve()
                .toBodilessEntity());
    }

    /** Sends Keycloak's "required actions" email (e.g. VERIFY_EMAIL, UPDATE_PASSWORD). Needs SMTP configured in the realm. */
    public void sendActionsEmail(String userId, List<String> actions, String clientId, String redirectUri,
            long lifespanSeconds) {
        execute(KeycloakResource.USER, userId, () -> keycloakRestClient.put()
                .uri(builder -> builder.path(USER_ACTIONS_EMAIL_PATH)
                        .queryParam("client_id", "{clientId}")
                        .queryParam("lifespan", "{lifespan}")
                        .queryParam("redirect_uri", "{redirectUri}")
                        .build(realm, userId, clientId, lifespanSeconds, redirectUri))
                .headers(adminAuthorization())
                .contentType(MediaType.APPLICATION_JSON)
                .body(actions)
                .retrieve()
                .toBodilessEntity());
    }

    // ------------------------------------------------------------------ roles

    public KeycloakRole getRealmRole(String roleName) {
        return execute(KeycloakResource.ROLE, roleName, () -> keycloakRestClient.get()
                .uri(ROLE_PATH, realm, roleName)
                .headers(adminAuthorization())
                .retrieve()
                .body(KeycloakRole.class));
    }

    public List<KeycloakRole> getCompositeRoles(String roleName) {
        List<KeycloakRole> roles = execute(KeycloakResource.ROLE, roleName, () -> keycloakRestClient.get()
                .uri(ROLE_COMPOSITES_PATH, realm, roleName)
                .headers(adminAuthorization())
                .retrieve()
                .body(ROLE_LIST));
        return roles != null ? roles : List.of();
    }

    public void createRealmRole(KeycloakRole role) {
        execute(KeycloakResource.ROLE, role.getName(), () -> keycloakRestClient.post()
                .uri(ROLES_PATH, realm)
                .headers(adminAuthorization())
                .contentType(MediaType.APPLICATION_JSON)
                .body(role)
                .retrieve()
                .toBodilessEntity());
    }

    public void deleteRealmRole(String roleName) {
        execute(KeycloakResource.ROLE, roleName, () -> keycloakRestClient.delete()
                .uri(ROLE_PATH, realm, roleName)
                .headers(adminAuthorization())
                .retrieve()
                .toBodilessEntity());
    }

    // ---------------------------------------------------------------- helpers

    private Consumer<HttpHeaders> adminAuthorization() {
        String token = getAdminAccessToken();
        return headers -> headers.setBearerAuth(token);
    }

    private <T> T execute(KeycloakResource resource, String entity, Supplier<T> keycloakCall) {
        try {
            return keycloakCall.get();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                forgetAdminToken();
            }
            throw KeycloakErrorTranslator.translate(ex, resource, entity);
        } catch (ResourceAccessException ex) {
            log.error("Cannot reach Keycloak at {}: {}", keycloakProperties.serverUrl(), ex.getMessage());
            throw new UserManagementException(ErrorCode.KEYCLOAK_UNREACHABLE, keycloakProperties.serverUrl(), ex);
        }
    }

    private record CachedToken(String value, Instant refreshAt) {
        boolean isAboutToExpire() {
            return Instant.now().isAfter(refreshAt);
        }
    }
}

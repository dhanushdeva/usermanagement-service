package com.usermanagement.identityprovider.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.usermanagement.config.KeycloakProperties;
import com.usermanagement.config.KeycloakProperties.ClientCredentials;
import com.usermanagement.config.KeycloakProperties.EmailVerification;
import com.usermanagement.dto.UserDto;
import com.usermanagement.exception.ErrorCode;
import com.usermanagement.exception.UserManagementException;

class KeycloakIdentityProviderTest {

    private static final String BASE = "http://keycloak.test";
    private static final String ADMIN = BASE + "/admin/realms/test-realm";
    private static final String TOKEN_JSON = "{\"access_token\":\"admin-token\",\"expires_in\":300}";

    private MockRestServiceServer keycloak;
    private KeycloakIdentityProvider identityProvider;

    @BeforeEach
    void setUp() {
        KeycloakProperties properties = new KeycloakProperties(BASE, "test-realm", null, null, false,
                new ClientCredentials("um-admin", "admin-secret"), new ClientCredentials("um-anonymous", "anon-secret"),
                new EmailVerification(false, null, null));
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        keycloak = MockRestServiceServer.bindTo(builder).build();
        identityProvider = new KeycloakIdentityProvider(new KeycloakAdminClient(builder.build(), properties), properties);
    }

    private void expectAdminToken() {
        keycloak.expect(requestTo(BASE + "/realms/test-realm/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=um-admin")))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
    }

    private void expectRole(String name) {
        keycloak.expect(requestTo(ADMIN + "/roles/" + name)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"r-" + name + "\",\"name\":\"" + name + "\"}", MediaType.APPLICATION_JSON));
    }

    @Test
    void registerCreatesUserWithAttributesPasswordAndRoles() {
        expectAdminToken();
        expectRole("CUSTOMER");
        keycloak.expect(requestTo(ADMIN + "/users")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.username").value("john"))
                .andExpect(jsonPath("$.attributes.entity_type[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.attributes.entity_id[0]").value("E1"))
                .andExpect(jsonPath("$.credentials[0].value").value("pw"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andRespond(withCreatedEntity(URI.create(ADMIN + "/users/new-id")));
        keycloak.expect(requestTo(ADMIN + "/users/new-id/role-mappings/realm")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$[0].name").value("CUSTOMER"))
                .andRespond(withNoContent());

        UserDto user = new UserDto();
        user.setUserName("john");
        user.setPassword("pw");
        user.setEntityType("CUSTOMER");
        user.setEntityId("E1");
        user.setRoles(List.of("CUSTOMER"));

        assertThat(identityProvider.registerUser(user)).isEqualTo("new-id");
        keycloak.verify();
    }

    @Test
    void registerWithUnknownRoleFailsBeforeCreatingUser() {
        expectAdminToken();
        keycloak.expect(requestTo(ADMIN + "/roles/MISSING")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        UserDto user = new UserDto();
        user.setUserName("john");
        user.setRoles(List.of("MISSING"));

        assertThatThrownBy(() -> identityProvider.registerUser(user))
                .isInstanceOfSatisfying(UserManagementException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROLE_NOT_FOUND));
        keycloak.verify();
    }

    @Test
    void updateMergesAttributesKeepsStatusAndPreservesDefaultRoles() {
        expectAdminToken();
        expectRole("ADMIN");
        keycloak.expect(requestTo(ADMIN + "/users/u1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"u1","username":"john","enabled":false,
                         "attributes":{"entity_type":["OLD"],"region":["EU"]}}
                        """, MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(ADMIN + "/users/u1")).andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.attributes.entity_type[0]").value("NEW"))
                .andExpect(jsonPath("$.attributes.region[0]").value("EU"))
                .andRespond(withNoContent());
        keycloak.expect(requestTo(ADMIN + "/users/u1/role-mappings/realm")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"name\":\"default-roles-test-realm\"},{\"name\":\"CUSTOMER\"}]",
                        MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(ADMIN + "/users/u1/role-mappings/realm")).andExpect(method(HttpMethod.DELETE))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("CUSTOMER"))
                .andRespond(withNoContent());
        keycloak.expect(requestTo(ADMIN + "/users/u1/role-mappings/realm")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$[0].name").value("ADMIN"))
                .andRespond(withNoContent());

        UserDto update = new UserDto();
        update.setUserId("u1");
        update.setEntityType("NEW");
        update.setRoles(List.of("ADMIN"));

        UserDto result = identityProvider.updateUser(update);

        assertThat(result.getEntityType()).isEqualTo("NEW");
        assertThat(result.getStatus()).isEqualTo("DISABLE");
        assertThat(result.getRoles()).containsExactly("ADMIN");
        keycloak.verify();
    }

    @Test
    void existingUserIsReportedAsConflict() {
        expectAdminToken();
        keycloak.expect(requestTo(ADMIN + "/users")).andRespond(withStatus(HttpStatus.CONFLICT));

        UserDto user = new UserDto();
        user.setUserName("john");

        assertThatThrownBy(() -> identityProvider.registerUser(user))
                .isInstanceOfSatisfying(UserManagementException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_ALREADY_EXISTS));
    }
}

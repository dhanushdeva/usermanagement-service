package com.usermanagement.datasource.provider.keycloak;

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

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;

import com.usermanagement.dto.User;
import com.usermanagement.entity.RoleDetails;
import com.usermanagement.exception.NotFoundException;
import com.usermanagement.exception.UserAlreadyExistsException;
import com.usermanagement.util.HttpHandler;

import tools.jackson.databind.json.JsonMapper;

/**
 * Tests the Keycloak flow (same calls as the original) against a mocked Keycloak.
 */
class KeycloakServiceProviderTest {

    private static final String BASE = "http://keycloak.test";
    private static final String TOKEN_URL = BASE + "/realms/test-realm/protocol/openid-connect/token";
    private static final String ADMIN = BASE + "/admin/realms/test-realm";
    private static final String TOKEN_JSON = "{\"access_token\":\"admin-token\",\"expires_in\":300}";

    private MockRestServiceServer keycloak;
    private KeycloakServiceProvider provider;

    @BeforeEach
    void setUp() {
        keycloak = MockRestServiceServer.bindTo(HttpHandler.restTemplate()).ignoreExpectOrder(false).build();
        provider = new KeycloakServiceProvider();
        ReflectionTestUtils.setField(provider, "serverUrl", BASE);
        ReflectionTestUtils.setField(provider, "adminRealm", "test-realm");
        ReflectionTestUtils.setField(provider, "adminClientId", "um-admin");
        ReflectionTestUtils.setField(provider, "adminClientSecret", "admin-secret");
        ReflectionTestUtils.setField(provider, "anonymousClientId", "um-anonymous");
        ReflectionTestUtils.setField(provider, "anonymousClientSecret", "anon-secret");
        ReflectionTestUtils.setField(provider, "enableEmailVerification", false);
        ReflectionTestUtils.setField(provider, "isTemporary", false);
        ReflectionTestUtils.setField(provider, "emailLifespan", 900);
        ReflectionTestUtils.setField(provider, "ssoRedirectUrl", "http://localhost:9012/*");
        ReflectionTestUtils.setField(provider, "mapper", JsonMapper.builder().build());
    }

    private void expectAdminToken() {
        keycloak.expect(requestTo(TOKEN_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(Matchers.containsString("client_id=um-admin")))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
    }

    private void expectAllRoles() {
        keycloak.expect(requestTo(ADMIN + "/roles")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"r1\",\"name\":\"CUSTOMER\"},{\"id\":\"r2\",\"name\":\"ADMIN\"}]",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    void wrongAdminClientSecretGivesUM114WithClientIdAsEntity() {
        keycloak.expect(requestTo(TOKEN_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"unauthorized_client\"}"));

        assertThatThrownBy(() -> provider.validateRole("CUSTOMER"))
                .isInstanceOfSatisfying(com.usermanagement.exception.KeycloakAdminClientException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo("UM114");
                    assertThat(ex.getEntity()).isEqualTo("um-admin");
                });
    }

    @Test
    void registerCreatesUserReadsItBackAndAssignsRoles() {
        expectAdminToken();
        expectAllRoles();
        keycloak.expect(requestTo(ADMIN + "/users")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.username").value("john"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.attributes.entity_type[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.attributes.entity_id[0]").value("E1"))
                .andExpect(jsonPath("$.credentials[0].value").value("pw"))
                .andRespond(withCreatedEntity(URI.create(ADMIN + "/users/new-id")));
        keycloak.expect(requestTo(ADMIN + "/users/new-id")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"new-id\",\"username\":\"john\"}", MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(ADMIN + "/users/new-id/role-mappings/realm")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$[0].name").value("CUSTOMER"))
                .andRespond(withNoContent());

        User user = new User();
        user.setUserName("john");
        user.setPassword("pw");
        user.setEntityType("CUSTOMER");
        user.setEntityId("E1");
        user.setRoles(List.of("CUSTOMER"));

        assertThat(provider.registerUser(user)).isEqualTo("new-id");
        keycloak.verify();
    }

    @Test
    void registerWithUnknownRoleFailsBeforeCreatingUser() {
        expectAdminToken();
        expectAllRoles();

        User user = new User();
        user.setUserName("john");
        user.setRoles(List.of("MISSING"));

        assertThatThrownBy(() -> provider.registerUser(user))
                .isInstanceOfSatisfying(NotFoundException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo("UM007");
                    assertThat(ex.getEntity()).isEqualTo("MISSING");
                });
        keycloak.verify();
    }

    @Test
    void existingUserIsReportedAsUM005() {
        expectAdminToken();
        expectAllRoles();
        keycloak.expect(requestTo(ADMIN + "/users")).andRespond(withStatus(HttpStatus.CONFLICT));

        User user = new User();
        user.setUserName("john");
        user.setRoles(List.of());

        assertThatThrownBy(() -> provider.registerUser(user))
                .isInstanceOfSatisfying(UserAlreadyExistsException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("UM005"));
    }

    @Test
    void updateReplacesAllRolesAndSendsOnlyRequestFields() {
        expectAdminToken();
        keycloak.expect(requestTo(ADMIN + "/users/u1/role-mappings/realm")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"name\":\"default-roles-test-realm\"},{\"name\":\"CUSTOMER\"}]",
                        MediaType.APPLICATION_JSON));
        expectAllRoles();
        keycloak.expect(requestTo(ADMIN + "/users/u1/role-mappings/realm")).andExpect(method(HttpMethod.DELETE))
                .andExpect(jsonPath("$.length()").value(2))
                .andRespond(withNoContent());
        keycloak.expect(requestTo(ADMIN + "/users/u1/role-mappings/realm")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$[0].name").value("ADMIN"))
                .andRespond(withNoContent());
        keycloak.expect(requestTo(ADMIN + "/users/u1")).andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.firstName").value("Johnny"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andRespond(withNoContent());

        User update = new User();
        update.setUserId("u1");
        update.setFirstName("Johnny");
        update.setRoles(List.of("ADMIN"));

        User result = provider.updateUser(update);

        assertThat(result.getFirstName()).isEqualTo("Johnny");
        assertThat(result.getStatus()).isEqualTo("ENABLE");
        keycloak.verify();
    }

    @Test
    void changeUserStatusPutsOnlyEnabledFlag() {
        expectAdminToken();
        keycloak.expect(requestTo(ADMIN + "/users/u1")).andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.enabled").value(false))
                .andRespond(withNoContent());

        assertThat(provider.changeUserStatus("u1", "DISABLE")).isTrue();
        keycloak.verify();
    }

    @Test
    void createRoleIfNotExistCreatesMissingRole() {
        keycloak.expect(ExpectedCount.twice(), requestTo(TOKEN_URL))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(ADMIN + "/roles/NEW_ROLE")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        keycloak.expect(requestTo(ADMIN + "/roles")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.name").value("NEW_ROLE"))
                .andRespond(withStatus(HttpStatus.CREATED));

        RoleDetails response = provider.createRoleIfNotExist("NEW_ROLE");

        assertThat(response.getName()).isEqualTo("NEW_ROLE");
        assertThat(response.getComposite()).isEmpty();
    }

    @Test
    void validateRoleChecksTheRealmRoleList() {
        expectAdminToken();
        expectAllRoles();

        assertThat(provider.validateRole("NOPE")).isFalse();
    }

    @Test
    void getUserByIdReturnsAllAttributesAsAdditionalAttributes() {
        expectAdminToken();
        keycloak.expect(requestTo(ADMIN + "/users/u1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"u1\",\"username\":\"john\",\"attributes\":{\"entity_type\":[\"CUSTOMER\"]}}",
                        MediaType.APPLICATION_JSON));

        User user = provider.getUserById("u1");

        assertThat(user.getUserName()).isEqualTo("john");
        assertThat(user.getAdditionalAttributes()).extracting("key").containsExactly("entity_type");
    }
}

package com.usermanagement.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.usermanagement.dto.User;
import com.usermanagement.entity.AnonymousUser;
import com.usermanagement.exception.ErrorCodeConstants;
import com.usermanagement.exception.UserAlreadyExistsException;
import com.usermanagement.service.UserManagementService;

@SpringBootTest
@AutoConfigureMockMvc
class UserManagementControllerTest {

    private static final String REGISTER_BODY = """
            {"user": {"userName": "john", "password": "secret", "email": "john@example.com", "roles": ["CUSTOMER"], "status": "ENABLE"}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserManagementService userManagementService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    /** Caller whose token has the realm role configured in apipermission.api.* (PM_CREATE). */
    private static JwtRequestPostProcessor permitted() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_PM_CREATE"));
    }

    @Test
    void anonymousIsPublic() throws Exception {
        var anonymousUser = new AnonymousUser();
        anonymousUser.setAccessToken("access");
        given(userManagementService.createAnonymousUser()).willReturn(anonymousUser);

        mockMvc.perform(post("/usermanagement/anonymous"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"));
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/usermanagement/getUsers").param("search", "x")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutPermissionReturns403UM112() throws Exception {
        mockMvc.perform(get("/usermanagement/getUsers").param("search", "x")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.httpStatus").value(403))
                .andExpect(jsonPath("$.errorCode").value("UM112"))
                .andExpect(jsonPath("$.details.errorCode").value("UM112"));
    }

    @Test
    void registerReturns201WithUserId() throws Exception {
        given(userManagementService.registerUser(any(User.class))).willReturn("new-id");

        mockMvc.perform(post("/usermanagement/register").with(permitted())
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("new-id"))
                .andExpect(jsonPath("$.userName").value("john"));
    }

    @Test
    void registerWithoutUserReturnsUM107() throws Exception {
        mockMvc.perform(post("/usermanagement/register").with(permitted())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UM107"))
                .andExpect(jsonPath("$.details.entity").value("user"));
    }

    @Test
    void registerWithoutEmailReturnsUM113() throws Exception {
        mockMvc.perform(post("/usermanagement/register").with(permitted())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\": {\"userName\": \"john\", \"status\": \"ENABLE\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UM113"))
                .andExpect(jsonPath("$.details.entity").value("email"));
    }

    @Test
    void registerWithWrongStatusValueReturnsUM111() throws Exception {
        mockMvc.perform(post("/usermanagement/register").with(permitted())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\": {\"userName\": \"john\", \"email\": \"j@e.com\", \"roles\": [], \"status\": \"disabled\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UM111"))
                .andExpect(jsonPath("$.details.entity").value("status"));
    }

    @Test
    void registerWithoutRolesReturnsUM117() throws Exception {
        mockMvc.perform(post("/usermanagement/register").with(permitted())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\": {\"userName\": \"john\", \"email\": \"j@e.com\", \"status\": \"ENABLE\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UM117"))
                .andExpect(jsonPath("$.details.entity").value("roles"));
    }

    @Test
    void registerWithoutUserNameReturnsUM116() throws Exception {
        mockMvc.perform(post("/usermanagement/register").with(permitted())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\": {\"email\": \"j@e.com\", \"roles\": [], \"status\": \"ENABLE\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UM116"));
    }

    @Test
    void registerWithoutStatusReturnsUM111() throws Exception {
        mockMvc.perform(post("/usermanagement/register").with(permitted())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\": {\"userName\": \"john\", \"email\": \"john@example.com\", \"roles\": []}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UM111"))
                .andExpect(jsonPath("$.details.entity").value("status"));
    }

    @Test
    void existingUserReturns409UM005() throws Exception {
        given(userManagementService.registerUser(any(User.class))).willThrow(
                new UserAlreadyExistsException(ErrorCodeConstants.MSG_USER_EXISTS, ErrorCodeConstants.USER_EXISTS, "john"));

        mockMvc.perform(post("/usermanagement/register").with(permitted())
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("UM005"));
    }

    @Test
    void updateUserWithoutUserIdReturnsUM108() throws Exception {
        mockMvc.perform(post("/usermanagement/updateUser").with(permitted())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"user\": {\"firstName\": \"John\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UM108"));
    }

    @Test
    void deleteUsersWithoutIdsReturnsUM108() throws Exception {
        mockMvc.perform(delete("/usermanagement/deleteUsers").with(permitted())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UM108"));
    }

    @Test
    void changeUserStatusRejectsLowerCaseStatusWithUM111() throws Exception {
        mockMvc.perform(post("/usermanagement/changeUserStatus").with(permitted())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\": \"u1\", \"status\": \"enable\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UM111"));
    }

    @Test
    void changeUserStatusWithoutUserIdReturnsUM110() throws Exception {
        mockMvc.perform(post("/usermanagement/changeUserStatus").with(permitted())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ENABLE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UM110"))
                .andExpect(jsonPath("$.details.entity").value("user.id"));
    }

    @Test
    void addRoleWithoutNameReturnsUM109() throws Exception {
        mockMvc.perform(post("/usermanagement/addRole").with(permitted())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\": {\"description\": \"x\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UM109"));
    }

    @Test
    void validateIdpRoleReturns404WhenRoleMissing() throws Exception {
        given(userManagementService.validateRole("NOPE")).willReturn(false);

        mockMvc.perform(post("/usermanagement/validateIdpRole").with(permitted())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"configurations\": {\"role.name\": \"NOPE\"}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Role Not Found"));
    }

    @Test
    void validateIdpRoleWithoutRoleNameReturnsUM105() throws Exception {
        mockMvc.perform(post("/usermanagement/validateIdpRole").with(permitted())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"configurations\": {}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UM105"));
    }

    @Test
    void createRoleIfNotExistOnlyNeedsAValidToken() throws Exception {
        mockMvc.perform(get("/usermanagement/createRoleIfNotExist").param("roleName", "R1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk());
    }
}

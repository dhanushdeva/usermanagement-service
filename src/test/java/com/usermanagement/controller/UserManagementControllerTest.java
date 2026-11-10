package com.usermanagement.controller;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import com.usermanagement.dto.UserDto;
import com.usermanagement.entity.AnonymousUserResponse;
import com.usermanagement.exception.ErrorCode;
import com.usermanagement.exception.UserManagementException;
import com.usermanagement.service.UserManagementService;

@SpringBootTest
@AutoConfigureMockMvc
class UserManagementControllerTest {

    private static final String REGISTER_BODY = """
            {"user": {"userName": "john", "password": "secret", "email": "john@example.com", "roles": ["CUSTOMER"]}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserManagementService userManagementService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static JwtRequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER_MANAGEMENT_ADMIN"));
    }

    @Test
    void anonymousEndpointIsPublic() throws Exception {
        given(userManagementService.createAnonymousUser())
                .willReturn(new AnonymousUserResponse(null, "guest", "access", "refresh"));

        mockMvc.perform(post("/usermanagement/anonymous"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"));
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/usermanagement/updateUser").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void callerWithoutConfiguredRoleGets403() throws Exception {
        mockMvc.perform(post("/usermanagement/updateUser").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("UM112"));
    }

    @Test
    void registerReturns201WithUserIdAndWithoutPassword() throws Exception {
        given(userManagementService.registerUser(any(UserDto.class))).willReturn("new-id");

        mockMvc.perform(post("/usermanagement/register").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("new-id"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void registerWithoutUserReturns400() throws Exception {
        mockMvc.perform(post("/usermanagement/register").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UM107"));
    }

    @Test
    void existingUserReturns409() throws Exception {
        given(userManagementService.registerUser(any(UserDto.class)))
                .willThrow(new UserManagementException(ErrorCode.USER_ALREADY_EXISTS, "john"));

        mockMvc.perform(post("/usermanagement/register").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("UM005"));
    }

    @Test
    void invalidStatusReturns400() throws Exception {
        mockMvc.perform(post("/usermanagement/changeUserStatus").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\": \"u1\", \"status\": \"PAUSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UM111"));
    }
}

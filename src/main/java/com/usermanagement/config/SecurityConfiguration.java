package com.usermanagement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Roles allowed per API, read from {@code apipermission.api.*} (comma separated) - same keys as bm-usermanagement-v4.
 * Used in the controller as {@code @PreAuthorize("hasAnyRole(@securityConfiguration.getRegister())")}.
 * The roles are Keycloak realm roles of the caller's token (realm_access.roles).
 */
@Configuration("securityConfiguration")
@Getter
@Setter
public class SecurityConfiguration {

    @Value("#{'${apipermission.api.register}'.split(',')}")
    private String[] register;

    @Value("#{'${apipermission.api.deleteUsers}'.split(',')}")
    private String[] deleteUsers;

    @Value("#{'${apipermission.api.changeUserStatus}'.split(',')}")
    private String[] changeUserStatus;

    @Value("#{'${apipermission.api.retrieveAndDeleteAnonymousUser}'.split(',')}")
    private String[] retrieveAndDeleteAnonymousUser;

    @Value("#{'${apipermission.api.validateIdpRole}'.split(',')}")
    private String[] validateIdpRole;

    @Value("#{'${apipermission.api.addRole}'.split(',')}")
    private String[] addRole;

    @Value("#{'${apipermission.api.deleteRole}'.split(',')}")
    private String[] deleteRole;

    @Value("#{'${apipermission.api.updateUser}'.split(',')}")
    private String[] updateUser;

    @Value("#{'${apipermission.api.roleAndComposite}'.split(',')}")
    private String[] roleAndComposite;

    @Value("#{'${apipermission.api.userInformation}'.split(',')}")
    private String[] userInformation;

    @Value("#{'${apipermission.api.resendVerificationMail}'.split(',')}")
    private String[] resendVerificationMail;

    @Value("#{'${apipermission.api.users}'.split(',')}")
    private String[] users;
}

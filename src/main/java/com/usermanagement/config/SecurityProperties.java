package com.usermanagement.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under the {@code app.security.*} prefix in application.yaml.
 *
 * @param publicUrls URL patterns reachable without a bearer token
 * @param apiRoles   API name (e.g. {@code register-user}) to the Keycloak realm roles allowed to call it
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(List<String> publicUrls, Map<String, List<String>> apiRoles) {

    public SecurityProperties {
        publicUrls = publicUrls != null ? publicUrls : List.of();
        apiRoles = apiRoles != null ? apiRoles : Map.of();
    }
}

package com.usermanagement.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under the {@code keycloak.*} prefix in application.yaml.
 */
@ConfigurationProperties(prefix = "keycloak")
public record KeycloakProperties(
        String serverUrl,
        String realm,
        Duration connectTimeout,
        Duration readTimeout,
        boolean temporaryPassword,
        ClientCredentials adminClient,
        ClientCredentials anonymousClient,
        EmailVerification emailVerification) {

    public KeycloakProperties {
        connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(5);
        readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(10);
        emailVerification = emailVerification != null ? emailVerification : new EmailVerification(false, null, null);
    }

    public record ClientCredentials(String clientId, String clientSecret) {
    }

    public record EmailVerification(boolean enabled, Duration linkLifespan, String redirectUri) {

        public EmailVerification {
            linkLifespan = linkLifespan != null ? linkLifespan : Duration.ofMinutes(15);
        }
    }
}

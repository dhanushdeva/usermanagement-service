package com.usermanagement.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class KeycloakClientConfig {

    /**
     * HTTP client pointed at the Keycloak server, used for the token endpoint and the Admin REST API.
     */
    @Bean
    RestClient keycloakRestClient(KeycloakProperties keycloakProperties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(keycloakProperties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(keycloakProperties.readTimeout());

        return RestClient.builder()
                .baseUrl(keycloakProperties.serverUrl())
                .requestFactory(requestFactory)
                .build();
    }
}

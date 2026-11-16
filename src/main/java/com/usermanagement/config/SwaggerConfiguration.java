package com.usermanagement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Swagger UI at /swagger-ui/index.html with a bearer-token "Authorize" button (same scheme as the original).
 */
@Configuration
public class SwaggerConfiguration {

    private static final String SECURITY_SCHEME_NAME = "User Management Auth Scheme";

    @Value("${spring.application.name}")
    private String appName;

    @Value("${service.server_url}")
    private String serverUrl;

    private static final String APP_DESCRIPTION = """
            User Management Service helps manage users and their access across different applications and platforms.
            It provides a secure, enterprise-grade solution for user onboarding, role management, and access control.
            """;

    @Bean
    OpenAPI customOpenAPI() {
        return new OpenAPI()
                .addServersItem(new Server().url(serverUrl))
                .info(new Info().title(appName).description(APP_DESCRIPTION).version("local"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}

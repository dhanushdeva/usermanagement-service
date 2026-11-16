package com.usermanagement.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.usermanagement.security.KeycloakRealmRoleConverter;

/**
 * Plain Spring Security (OAuth2 resource server):
 * <ul>
 *   <li>URLs in {@code app.security.public-urls} are public, everything else needs a bearer token</li>
 *   <li>the token is a Keycloak JWT verified with the realm's public keys (spring.security...jwk-set-uri)</li>
 *   <li>realm_access.roles of the token become Spring roles (ROLE_*) for the @PreAuthorize checks</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class WebSecurityConfiguration {

    /** Comma separated list in application.yaml; Spring converts it to an array. */
    @Value("${app.security.public-urls}")
    private String[] publicUrls;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicUrls).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(publicUrlsIgnoreTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Public URLs ignore an Authorization header completely. Without this, Spring rejects an expired or invalid
     * token (for example one left in Swagger's Authorize dialog) with 401 even on a public endpoint such as /anonymous.
     */
    private BearerTokenResolver publicUrlsIgnoreTokenResolver() {
        var delegate = new DefaultBearerTokenResolver();
        var pathMatcher = PathPatternRequestMatcher.withDefaults();
        List<RequestMatcher> publicMatchers = Arrays.stream(publicUrls)
                .map(String::trim)
                .<RequestMatcher>map(pathMatcher::matcher)
                .toList();
        return request -> publicMatchers.stream().anyMatch(m -> m.matches(request)) ? null : delegate.resolve(request);
    }

    private static JwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}

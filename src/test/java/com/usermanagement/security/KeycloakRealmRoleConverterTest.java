package com.usermanagement.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("t").header("alg", "none").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    }

    @Test
    void mapsRealmRolesToPrefixedAuthorities() {
        Jwt jwt = token().claim("realm_access", Map.of("roles", List.of("USER_MANAGEMENT_ADMIN", "CUSTOMER"))).build();

        assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER_MANAGEMENT_ADMIN", "ROLE_CUSTOMER");
    }

    @Test
    void tokenWithoutRealmAccessHasNoAuthorities() {
        assertThat(converter.convert(token().claim("sub", "x").build())).isEmpty();
    }
}

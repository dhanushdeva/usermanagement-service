package com.usermanagement.security;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.usermanagement.config.SecurityProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Used in {@code @PreAuthorize("@apiAccess.isAllowed('register-user')")}. Allows the call when the caller's token
 * has at least one of the roles configured in {@code app.security.api-roles.<apiName>}.
 */
@Slf4j
@Component("apiAccess")
@RequiredArgsConstructor
public class ApiAccessChecker {

    private final SecurityProperties securityProperties;

    public boolean isAllowed(String apiName) {
        List<String> allowedRoles = securityProperties.apiRoles().getOrDefault(apiName, List.of());
        if (allowedRoles.isEmpty()) {
            log.warn("No roles configured in app.security.api-roles.{}; access denied", apiName);
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        Set<String> callerAuthorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        return allowedRoles.stream()
                .anyMatch(role -> callerAuthorities.contains(KeycloakRealmRoleConverter.ROLE_PREFIX + role));
    }
}

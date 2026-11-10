package com.usermanagement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body for /addRole: {@code {"roles": {"name": "...", "description": "..."}}} (JSON key kept as "roles").
 */
public record CreateRoleRequest(@JsonProperty("roles") RoleDto role) {
}

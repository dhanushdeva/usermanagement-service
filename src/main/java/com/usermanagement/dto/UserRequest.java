package com.usermanagement.dto;


/**
 * Body for /register and /updateUser: {@code {"user": {...}}}.
 */
public record UserRequest(UserDto user) {
}

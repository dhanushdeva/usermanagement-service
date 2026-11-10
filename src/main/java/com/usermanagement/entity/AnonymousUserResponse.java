package com.usermanagement.entity;

public record AnonymousUserResponse(String userId, String userName, String accessToken, String refreshToken) {
}

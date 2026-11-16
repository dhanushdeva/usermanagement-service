package com.usermanagement.entity;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(name = "AnonymousUser", description = "Anonymous user details including token")
public class AnonymousUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "User Id")
    private String userId;
    @Schema(description = "User Name")
    private String userName;
    @Schema(description = "Access Token is used for authorization")
    private String accessToken;
    @Schema(description = "Refresh Token is used to obtain a new access token")
    private String refreshToken;
}

package com.usermanagement.entity;

import java.io.Serializable;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Token implements Serializable {

    private static final long serialVersionUID = 1L;

    private String accessToken;
    private String refreshToken;
}

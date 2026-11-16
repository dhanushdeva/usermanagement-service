package com.usermanagement.entity;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleDetails {

    private String name;
    private List<String> composite;
}

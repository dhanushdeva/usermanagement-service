package com.usermanagement.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Attribute implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "The key of the attribute")
    private String key;
    @Schema(description = "The value of the attribute")
    private String value;
}

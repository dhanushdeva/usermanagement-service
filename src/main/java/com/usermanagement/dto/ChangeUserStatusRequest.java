package com.usermanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ChangeUserStatusRequest(
        String userId,
        @Schema(description = "ENABLE or DISABLE") String status) {
}

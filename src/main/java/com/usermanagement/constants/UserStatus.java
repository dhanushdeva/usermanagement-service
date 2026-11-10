package com.usermanagement.constants;

import java.util.Arrays;
import java.util.Optional;

public enum UserStatus {
    ENABLE,
    DISABLE;

    public static Optional<UserStatus> fromValue(String value) {
        return Arrays.stream(values()).filter(status -> status.name().equalsIgnoreCase(value)).findFirst();
    }

    public static UserStatus fromEnabled(Boolean enabled) {
        return Boolean.FALSE.equals(enabled) ? DISABLE : ENABLE;
    }

    public boolean isEnabled() {
        return this == ENABLE;
    }
}

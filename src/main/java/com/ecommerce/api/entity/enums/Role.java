package com.ecommerce.api.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Access control roles assigned to system users.
 * Features resilient JSON serialization and case-insensitive deserialization.
 */
public enum Role {
    ROLE_CUSTOMER, ROLE_ADMIN;

    @JsonValue
    public String toValue() {
        return name();
    }

    @JsonCreator
    public static Role fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase();

        for (Role role : values()) {
            if (role.name().equals(normalized)) {
                return role;
            }
        }

        throw new IllegalArgumentException("Unknown role: '" + value + "'. Supported roles: " + Arrays.toString(values()));
    }
}

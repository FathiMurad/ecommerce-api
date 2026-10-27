package com.ecommerce.api.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Lifecycle states of a payment transaction in the supermarket system.
 * Features resilient JSON serialization and case-insensitive deserialization.
 */
public enum PaymentStatus {
    PENDING, COMPLETED, FAILED, REFUNDED;

    @JsonValue
    public String toValue() {
        return name();
    }

    @JsonCreator
    public static PaymentStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase();

        for (PaymentStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }

        throw new IllegalArgumentException("Unknown payment status: '" + value + "'. Supported statuses: " + Arrays.toString(values()));
    }
}

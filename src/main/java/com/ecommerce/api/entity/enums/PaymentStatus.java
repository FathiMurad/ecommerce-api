package com.ecommerce.api.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

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

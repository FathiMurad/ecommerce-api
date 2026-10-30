package com.ecommerce.api.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum PaymentMethod {
    CREDIT_CARD, DEBIT_CARD, PAYPAL, CASH_ON_DELIVERY;

    @JsonValue
    public String toValue() {
        return name();
    }

    @JsonCreator
    public static PaymentMethod fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");

        for (PaymentMethod method : values()) {
            if (method.name().equals(normalized)) {
                return method;
            }
        }

        throw new IllegalArgumentException("Unknown payment method: '" + value + "'. Supported methods: " + Arrays.toString(values()));
    }
}

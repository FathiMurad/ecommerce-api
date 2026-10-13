package com.ecommerce.api.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Enumeration representing the supported payment methods within the supermarket system.
 * Features resilient JSON serialization and case-insensitive deserialization.
 */
public enum PaymentMethod {
    CREDIT_CARD, DEBIT_CARD, PAYPAL, CASH_ON_DELIVERY;

    @JsonValue
    public String toValue() {
        return name();
    }

    @JsonCreator
    public static PaymentMethod fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");

        return Arrays.stream(PaymentMethod.values()).filter(method -> method.name().equals(normalized)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown payment method: '" + value + "'. Supported values: " + Arrays.toString(PaymentMethod.values())));
    }
}

package com.ecommerce.api.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents the lifecycle stages of an order from placement to delivery or cancellation,
 * encapsulating domain state transition rules.
 */
public enum OrderStatus {
    PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(PENDING, EnumSet.of(CONFIRMED, CANCELLED), CONFIRMED, EnumSet.of(SHIPPED, CANCELLED), SHIPPED, EnumSet.of(DELIVERED), DELIVERED, EnumSet.noneOf(OrderStatus.class), CANCELLED, EnumSet.noneOf(OrderStatus.class));

    public boolean canTransitionTo(OrderStatus target) {
        if (target == null) {
            return false;
        }
        return VALID_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(OrderStatus.class)).contains(target);
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }

    @JsonValue
    public String toValue() {
        return name();
    }

    @JsonCreator
    public static OrderStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase();

        for (OrderStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }

        throw new IllegalArgumentException("Unknown order status: '" + value + "'. Supported statuses: " + Arrays.toString(values()));
    }
}

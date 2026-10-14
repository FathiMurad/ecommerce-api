package com.ecommerce.api.constant;

/**
 * Shared financial constraints across the e-commerce system.
 * Serves as the Single Source of Truth (SSOT) for monetary precision and scale.
 */
public final class MoneyConstants {

    private MoneyConstants() {
        // Prevent instantiation
    }

    public static final int PRECISION = 12;
    public static final int SCALE = 2;
}

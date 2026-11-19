package com.ecommerce.api.security.ratelimit;

/**
 * Defines endpoint categories subject to distinct rate-limiting policies.
 */
public enum RateLimitType {
    AUTH,
    CHECKOUT,
    PAYMENT,
    CART
}

package com.ecommerce.api.event;

import java.math.BigDecimal;

/**
 * Immutable domain event published when an order is created during checkout.
 */
public record OrderPlacedEvent(
        Long orderId,
        String trackingNumber,
        String customerEmail,
        String customerName,
        BigDecimal totalAmount
) {
}

package com.ecommerce.api.event;

import java.math.BigDecimal;

/**
 * Immutable domain event dispatched when an order is successfully persisted during checkout.
 *
 * @param orderId        Database identifier of the order.
 * @param trackingNumber Unique public order tracking identifier.
 * @param customerEmail  Email address of the purchasing customer.
 * @param customerName   Full name of the purchasing customer.
 * @param totalAmount    Authoritative monetary sum calculated for the order.
 */
public record OrderPlacedEvent(Long orderId, String trackingNumber, String customerEmail, String customerName,
                               BigDecimal totalAmount) {
}

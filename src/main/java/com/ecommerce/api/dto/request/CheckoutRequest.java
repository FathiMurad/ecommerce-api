package com.ecommerce.api.dto.request;

/**
 * Payload required to convert an active shopping cart into a confirmed order.
 */
public record CheckoutRequest(String customerName, String customerEmail, String shippingAddress) {
}

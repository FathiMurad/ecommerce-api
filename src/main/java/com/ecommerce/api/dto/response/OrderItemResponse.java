package com.ecommerce.api.dto.response;

import com.ecommerce.api.entity.OrderItem;

import java.math.BigDecimal;

/**
 * Read-only projection of a purchased line item with historical pricing.
 */
public record OrderItemResponse(Long id, Long productId, String productName, Integer quantity,
                                BigDecimal priceAtPurchase, BigDecimal subtotal) {
    public static OrderItemResponse fromEntity(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProduct().getId(), item.getProduct().getName(), item.getQuantity(), item.getPriceAtPurchase(), item.getSubtotal());
    }
}

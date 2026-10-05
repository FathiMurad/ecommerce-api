package com.ecommerce.api.dto.response;

import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.OrderItem;
import com.ecommerce.api.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only projection representing a full customer order.
 */
public record OrderResponse(Long id, String trackingNumber, String customerName, String customerEmail,
                            String shippingAddress, OrderStatus status, BigDecimal totalAmount,
                            List<OrderItemResponse> items, LocalDateTime createdAt) {
    public static OrderResponse fromEntity(Order order) {
        List<OrderItemResponse> itemResponses = new ArrayList<>();

        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                itemResponses.add(OrderItemResponse.fromEntity(item));
            }
        }

        return new OrderResponse(order.getId(), order.getTrackingNumber(), order.getCustomerName(), order.getCustomerEmail(), order.getShippingAddress(), order.getStatus(), order.getTotalAmount(), itemResponses, order.getCreatedAt());
    }
}

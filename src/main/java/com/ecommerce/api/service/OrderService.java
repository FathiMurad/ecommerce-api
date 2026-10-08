package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.response.OrderResponse;
import com.ecommerce.api.dto.response.PagedResponse;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.entity.enums.OrderStatus;
import org.springframework.data.domain.Pageable;

/**
 * Service contract defining order placement, lifecycle transitions, and retrieval operations.
 */
public interface OrderService {

    /**
     * Executes the checkout transaction: validates cart and stock, deducts inventory,
     * creates the order snapshot, and clears the cart.
     * Supports both authenticated users and guest sessions.
     */
    OrderResponse checkout(User user, String cartToken, CheckoutRequest request);

    /**
     * Backward-compatible checkout overload for guest sessions with only a cart token.
     */
    default OrderResponse checkout(String cartToken, CheckoutRequest request) {
        return checkout(null, cartToken, request);
    }

    /**
     * Finds an order by its unique tracking number.
     */
    OrderResponse getOrderByTrackingNumber(String trackingNumber);

    /**
     * Retrieves paged order history for a customer email.
     */
    PagedResponse<OrderResponse> getOrdersByCustomerEmail(String customerEmail, Pageable pageable);

    /**
     * Retrieves paged order history for an authenticated user.
     */
    PagedResponse<OrderResponse> getMyOrders(User user, Pageable pageable);

    /**
     * Administratively updates the lifecycle status of an order enforcing state machine rules.
     *
     * @param trackingNumber The unique order tracking identifier.
     * @param newStatus      Target lifecycle status.
     * @return Updated OrderResponse details.
     */
    OrderResponse updateOrderStatus(String trackingNumber, OrderStatus newStatus);
}

package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.response.OrderResponse;
import com.ecommerce.api.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;

/**
 * Service contract defining order placement and order retrieval operations.
 */
public interface OrderService {

    /**
     * Executes the checkout transaction: validates cart and stock, deducts inventory,
     * creates the order snapshot, and clears the cart.
     */
    OrderResponse checkout(String cartToken, CheckoutRequest request);

    /**
     * Finds an order by its unique tracking number.
     */
    OrderResponse getOrderByTrackingNumber(String trackingNumber);

    /**
     * Retrieves paged order history for a customer email.
     */
    PagedResponse<OrderResponse> getOrdersByCustomerEmail(String customerEmail, Pageable pageable);
}

package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.response.OrderResponse;
import com.ecommerce.api.dto.response.PagedResponse;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.entity.enums.OrderStatus;
import org.springframework.data.domain.Pageable;

/**
 * Service for managing orders and checkout operations.
 */
public interface OrderService {

    OrderResponse checkout(User user, String cartToken, CheckoutRequest request);

    OrderResponse getOrderByTrackingNumber(String trackingNumber, User viewer);

    PagedResponse<OrderResponse> getOrdersByCustomerEmail(String customerEmail, Pageable pageable);

    PagedResponse<OrderResponse> getMyOrders(User user, Pageable pageable);

    OrderResponse updateOrderStatus(String trackingNumber, OrderStatus newStatus);
}

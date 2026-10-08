package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.request.UpdateOrderStatusRequest;
import com.ecommerce.api.dto.response.OrderResponse;
import com.ecommerce.api.dto.response.PagedResponse;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.security.SecurityUser;
import com.ecommerce.api.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing endpoints for order placement, lifecycle management, and history.
 * Supports both authenticated users via JWT and guest checkouts.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Executes atomic order checkout from the user's active shopping cart.
     */
    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(@AuthenticationPrincipal SecurityUser securityUser, @RequestHeader(value = "X-Cart-Token", required = false) String cartToken, @Valid @RequestBody CheckoutRequest request) {
        User user = extractUser(securityUser);
        OrderResponse order = orderService.checkout(user, cartToken, request);
        return new ResponseEntity<>(order, HttpStatus.CREATED);
    }

    /**
     * Retrieves paginated order history for the currently authenticated user.
     */
    @GetMapping("/my-orders")
    public ResponseEntity<PagedResponse<OrderResponse>> getMyOrders(@AuthenticationPrincipal SecurityUser securityUser, @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        User user = extractUser(securityUser);
        PagedResponse<OrderResponse> orders = orderService.getMyOrders(user, pageable);
        return ResponseEntity.ok(orders);
    }

    /**
     * Administratively updates the lifecycle status of an order.
     */
    @PatchMapping("/{trackingNumber}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(@PathVariable String trackingNumber, @Valid @RequestBody UpdateOrderStatusRequest request) {
        OrderResponse updatedOrder = orderService.updateOrderStatus(trackingNumber, request.getStatus());
        return ResponseEntity.ok(updatedOrder);
    }

    /**
     * Retrieves an order by its unique tracking number.
     */
    @GetMapping("/{trackingNumber}")
    public ResponseEntity<OrderResponse> getOrderByTrackingNumber(@PathVariable String trackingNumber) {
        OrderResponse order = orderService.getOrderByTrackingNumber(trackingNumber);
        return ResponseEntity.ok(order);
    }

    /**
     * Retrieves order history filtered by customer email (administrative or guest lookup).
     */
    @GetMapping
    public ResponseEntity<PagedResponse<OrderResponse>> getOrdersByCustomerEmail(@RequestParam String customerEmail, @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PagedResponse<OrderResponse> orders = orderService.getOrdersByCustomerEmail(customerEmail, pageable);
        return ResponseEntity.ok(orders);
    }

    private User extractUser(SecurityUser securityUser) {
        return securityUser != null ? securityUser.getUser() : null;
    }
}

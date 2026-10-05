package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.response.OrderResponse;
import com.ecommerce.api.dto.response.PagedResponse;
import com.ecommerce.api.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing endpoints for order placement and tracking.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(@RequestHeader("X-Cart-Token") String cartToken, @RequestBody CheckoutRequest request) {
        OrderResponse order = orderService.checkout(cartToken, request);
        return new ResponseEntity<>(order, HttpStatus.CREATED);
    }

    @GetMapping("/{trackingNumber}")
    public ResponseEntity<OrderResponse> getOrderByTrackingNumber(@PathVariable String trackingNumber) {
        OrderResponse order = orderService.getOrderByTrackingNumber(trackingNumber);
        return ResponseEntity.ok(order);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<OrderResponse>> getOrdersByCustomerEmail(@RequestParam String customerEmail, @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PagedResponse<OrderResponse> orders = orderService.getOrdersByCustomerEmail(customerEmail, pageable);
        return ResponseEntity.ok(orders);
    }
}

package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.request.UpdateOrderStatusRequest;
import com.ecommerce.api.dto.response.OrderResponse;
import com.ecommerce.api.dto.response.PagedResponse;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.security.SecurityUser;
import com.ecommerce.api.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * Supports authenticated customers, anonymous guest checkouts, and administrative management.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Endpoints for order placement, tracking, lifecycle transitions, and order history")
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Execute order checkout", description = "Converts items in the active cart into a confirmed order, decrements stock atomically, and clears the cart.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "Order placed successfully", content = @Content(schema = @Schema(implementation = OrderResponse.class))), @ApiResponse(responseCode = "400", description = "Cart is empty, insufficient stock, or invalid checkout payload", content = @Content), @ApiResponse(responseCode = "404", description = "Cart not found", content = @Content)})
    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(@AuthenticationPrincipal SecurityUser securityUser, @Parameter(description = "Anonymous cart session token (required for guest checkout)") @RequestHeader(value = "X-Cart-Token", required = false) String cartToken, @Valid @RequestBody CheckoutRequest request) {
        User user = extractUser(securityUser);
        OrderResponse order = orderService.checkout(user, cartToken, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @Operation(summary = "Get current user order history (Paginated)", description = "Retrieves paginated order history for the currently authenticated user. Requires authentication.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successfully retrieved order history", content = @Content(schema = @Schema(implementation = PagedResponse.class))), @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)})
    @GetMapping("/my-orders")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PagedResponse<OrderResponse>> getMyOrders(@AuthenticationPrincipal SecurityUser securityUser, @Parameter(description = "Pagination parameters (page, size, sort)") @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        User user = extractUser(securityUser);
        PagedResponse<OrderResponse> orders = orderService.getMyOrders(user, pageable);
        return ResponseEntity.ok(orders);
    }

    @Operation(summary = "Update order status", description = "Administratively updates the lifecycle status of an order according to the state machine rules. Requires ROLE_ADMIN.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Order status updated successfully", content = @Content(schema = @Schema(implementation = OrderResponse.class))), @ApiResponse(responseCode = "400", description = "Illegal status transition or invalid status value", content = @Content), @ApiResponse(responseCode = "403", description = "Access denied - Administrator role required", content = @Content), @ApiResponse(responseCode = "404", description = "Order not found", content = @Content)})
    @PatchMapping("/{trackingNumber}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> updateOrderStatus(@Parameter(description = "Order tracking number", required = true) @PathVariable String trackingNumber, @Valid @RequestBody UpdateOrderStatusRequest request) {
        OrderResponse updatedOrder = orderService.updateOrderStatus(trackingNumber, request.getStatus());
        return ResponseEntity.ok(updatedOrder);
    }

    @Operation(summary = "Get order by tracking number", description = "Retrieves order details by its unique tracking number. Subject to ownership or admin access control.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Order retrieved successfully", content = @Content(schema = @Schema(implementation = OrderResponse.class))), @ApiResponse(responseCode = "403", description = "Unauthorized access to another user's order", content = @Content), @ApiResponse(responseCode = "404", description = "Order not found", content = @Content)})
    @GetMapping("/{trackingNumber}")
    public ResponseEntity<OrderResponse> getOrderByTrackingNumber(@Parameter(description = "Order tracking number", required = true) @PathVariable String trackingNumber) {
        OrderResponse order = orderService.getOrderByTrackingNumber(trackingNumber);
        return ResponseEntity.ok(order);
    }

    @Operation(summary = "Get orders by customer email (Paginated)", description = "Retrieves order history filtered by customer email. Restricted to ROLE_ADMIN or the matching authenticated customer.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successfully retrieved orders for email", content = @Content(schema = @Schema(implementation = PagedResponse.class))), @ApiResponse(responseCode = "403", description = "Access denied - cannot view another customer's orders", content = @Content)})
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or (isAuthenticated() and authentication.name.equalsIgnoreCase(#customerEmail))")
    public ResponseEntity<PagedResponse<OrderResponse>> getOrdersByCustomerEmail(@Parameter(description = "Target customer email address", required = true) @RequestParam String customerEmail, @Parameter(description = "Pagination parameters") @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PagedResponse<OrderResponse> orders = orderService.getOrdersByCustomerEmail(customerEmail, pageable);
        return ResponseEntity.ok(orders);
    }

    private User extractUser(SecurityUser securityUser) {
        return securityUser != null ? securityUser.getUser() : null;
    }
}

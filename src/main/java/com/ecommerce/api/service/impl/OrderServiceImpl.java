package com.ecommerce.api.service.impl;

import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.response.OrderResponse;
import com.ecommerce.api.dto.response.PagedResponse;
import com.ecommerce.api.entity.Cart;
import com.ecommerce.api.entity.CartItem;
import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.OrderItem;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.entity.enums.OrderStatus;
import com.ecommerce.api.exception.BadRequestException;
import com.ecommerce.api.exception.EmptyCartException;
import com.ecommerce.api.exception.InsufficientStockException;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.CartRepository;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of OrderService providing atomic checkout execution,
 * stock synchronization, and strict order lifecycle management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    @Override
    public OrderResponse checkout(User user, String cartToken, CheckoutRequest request) {
        Cart cart = resolveCartForCheckout(user, cartToken);

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new EmptyCartException("Cannot checkout with an empty cart.");
        }

        String customerEmail = resolveCustomerEmail(user, request);
        String customerName = resolveCustomerName(user, request);
        String shippingAddress = (request != null && request.shippingAddress() != null) ? request.shippingAddress().trim() : null;

        if (customerEmail == null || customerEmail.isEmpty()) {
            throw new IllegalArgumentException("Customer email is required for checkout.");
        }
        if (customerName == null || customerName.isEmpty()) {
            throw new IllegalArgumentException("Customer name is required for checkout.");
        }
        if (shippingAddress == null || shippingAddress.isEmpty()) {
            throw new IllegalArgumentException("Shipping address is required for checkout.");
        }

        String trackingNumber = generateUniqueTrackingNumber();

        Order order = Order.builder().trackingNumber(trackingNumber).user(user).customerName(customerName).customerEmail(customerEmail).shippingAddress(shippingAddress).status(OrderStatus.CONFIRMED).items(new ArrayList<>()).build();

        for (CartItem cartItem : cart.getItems()) {
            Long productId = cartItem.getProduct().getId();
            Product product = productRepository.findById(productId).orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

            if (product.getStockQuantity() < cartItem.getQuantity()) {
                throw new InsufficientStockException("Insufficient stock for product '" + product.getName() + "'. Available: " + product.getStockQuantity() + ", Requested: " + cartItem.getQuantity());
            }

            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            productRepository.save(product);

            OrderItem orderItem = OrderItem.builder().product(product).quantity(cartItem.getQuantity()).priceAtPurchase(product.getPrice()).build();

            order.addItem(orderItem);
        }

        order.setTotalAmount(order.calculateTotalAmount());
        Order savedOrder = orderRepository.save(order);

        cart.clear();
        cartRepository.save(cart);

        log.info("Order created successfully with trackingNumber={} for customerEmail={}, totalAmount={}", savedOrder.getTrackingNumber(), savedOrder.getCustomerEmail(), savedOrder.getTotalAmount());

        return OrderResponse.fromEntity(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByTrackingNumber(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Tracking number cannot be null or empty.");
        }
        Order order = orderRepository.findByTrackingNumberWithItems(trackingNumber.trim()).orElseThrow(() -> new ResourceNotFoundException("Order not found with tracking number: " + trackingNumber));
        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getOrdersByCustomerEmail(String customerEmail, Pageable pageable) {
        if (customerEmail == null || customerEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer email cannot be null or empty.");
        }
        Page<Order> orderPage = orderRepository.findByCustomerEmail(customerEmail.trim(), pageable);
        List<OrderResponse> content = orderPage.getContent().stream().map(OrderResponse::fromEntity).toList();

        return new PagedResponse<>(content, orderPage.getNumber(), orderPage.getSize(), orderPage.getTotalElements(), orderPage.getTotalPages(), orderPage.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getMyOrders(User user, Pageable pageable) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User cannot be null when fetching order history.");
        }
        Page<Order> orderPage = orderRepository.findByUserId(user.getId(), pageable);
        List<OrderResponse> content = orderPage.getContent().stream().map(OrderResponse::fromEntity).toList();

        return new PagedResponse<>(content, orderPage.getNumber(), orderPage.getSize(), orderPage.getTotalElements(), orderPage.getTotalPages(), orderPage.isLast());
    }

    @Override
    public OrderResponse updateOrderStatus(String trackingNumber, OrderStatus newStatus) {
        if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Tracking number cannot be null or empty.");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("New status cannot be null.");
        }

        Order order = orderRepository.findByTrackingNumberWithItems(trackingNumber.trim()).orElseThrow(() -> new ResourceNotFoundException("Order not found with tracking number: " + trackingNumber));

        OrderStatus currentStatus = order.getStatus();

        // 1. Validation: No action needed if status is unchanged
        if (currentStatus == newStatus) {
            return OrderResponse.fromEntity(order);
        }

        // 2. Validation: Terminal states cannot transition
        if (currentStatus == OrderStatus.DELIVERED || currentStatus == OrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot modify an order already in terminal status: " + currentStatus);
        }

        // 3. State Machine transitions
        switch (newStatus) {
            case CONFIRMED -> {
                if (currentStatus != OrderStatus.PENDING) {
                    throw new BadRequestException("Only PENDING orders can be moved to CONFIRMED. Current status: " + currentStatus);
                }
            }
            case SHIPPED -> {
                if (currentStatus != OrderStatus.CONFIRMED) {
                    throw new BadRequestException("Only CONFIRMED orders can be dispatched to SHIPPED. Current status: " + currentStatus);
                }
            }
            case DELIVERED -> {
                if (currentStatus != OrderStatus.SHIPPED) {
                    throw new BadRequestException("Only SHIPPED orders can be marked as DELIVERED. Current status: " + currentStatus);
                }
            }
            case CANCELLED -> {
                if (currentStatus != OrderStatus.PENDING && currentStatus != OrderStatus.CONFIRMED) {
                    throw new BadRequestException("Only PENDING or CONFIRMED orders can be cancelled. Current status: " + currentStatus);
                }
                // Return stock back to inventory if order is cancelled
                for (OrderItem item : order.getItems()) {
                    Long productId = item.getProduct().getId();
                    Product product = productRepository.findById(productId).orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
                    product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                    productRepository.save(product);
                }
            }
            default -> throw new BadRequestException("Unsupported status transition to: " + newStatus);
        }

        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);

        log.info("Order status updated for trackingNumber={}: {} -> {}", trackingNumber, currentStatus, newStatus);

        return OrderResponse.fromEntity(updatedOrder);
    }

    /**
     * Resolves the cart to checkout, prioritizing the authenticated user's cart
     * while preventing IDOR and unauthorized cart hijacking.
     */
    private Cart resolveCartForCheckout(User user, String cartToken) {
        if (user != null) {
            Optional<Cart> userCart = cartRepository.findByUserIdWithItems(user.getId());
            if (userCart.isPresent()) {
                return userCart.get();
            }

            if (cartToken != null && !cartToken.trim().isEmpty()) {
                Optional<Cart> tokenCart = cartRepository.findByCartTokenWithItems(cartToken.trim());
                if (tokenCart.isPresent()) {
                    Cart cart = tokenCart.get();
                    if (cart.getUser() == null || cart.getUser().getId().equals(user.getId())) {
                        return cart;
                    }
                    log.warn("User id={} attempted to checkout cart id={} belonging to another user", user.getId(), cart.getId());
                }
            }

            throw new ResourceNotFoundException("No active shopping cart found for user.");
        }

        // Guest checkout
        if (cartToken != null && !cartToken.trim().isEmpty()) {
            Optional<Cart> tokenCart = cartRepository.findByCartTokenWithItems(cartToken.trim());
            if (tokenCart.isPresent()) {
                Cart cart = tokenCart.get();
                if (cart.getUser() == null) {
                    return cart;
                }
                log.warn("Guest attempted to checkout authenticated user cart via token: {}", cartToken);
                throw new BadRequestException("The specified cart belongs to a registered user. Please log in to complete checkout.");
            }
        }

        throw new ResourceNotFoundException("No active shopping cart found for checkout.");
    }

    private String resolveCustomerEmail(User user, CheckoutRequest request) {
        if (user != null && user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            return user.getEmail().trim();
        }
        if (request != null && request.customerEmail() != null && !request.customerEmail().trim().isEmpty()) {
            return request.customerEmail().trim();
        }
        return null;
    }

    private String resolveCustomerName(User user, CheckoutRequest request) {
        if (user != null) {
            String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
            String last = user.getLastName() != null ? user.getLastName().trim() : "";
            String full = (first + " " + last).trim();
            if (!full.isEmpty()) {
                return full;
            }
        }
        if (request != null && request.customerName() != null && !request.customerName().trim().isEmpty()) {
            return request.customerName().trim();
        }
        return null;
    }

    private String generateUniqueTrackingNumber() {
        String trackingNumber;
        do {
            trackingNumber = "ORD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        } while (orderRepository.findByTrackingNumber(trackingNumber).isPresent());
        return trackingNumber;
    }
}

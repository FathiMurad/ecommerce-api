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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of OrderService providing atomic checkout execution and order lifecycle management.
 */
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

        String trackingNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Resolve customer details from authenticated user or request payload
        String customerEmail = (user != null && user.getEmail() != null) ? user.getEmail() : (request != null ? request.customerEmail() : null);
        String customerName = (user != null) ? (user.getFirstName() + " " + user.getLastName()).trim() : (request != null ? request.customerName() : null);
        String shippingAddress = request != null ? request.shippingAddress() : null;

        if (customerEmail == null || customerEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer email is required for checkout.");
        }
        if (customerName == null || customerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name is required for checkout.");
        }
        if (shippingAddress == null || shippingAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("Shipping address is required for checkout.");
        }

        Order order = Order.builder().trackingNumber(trackingNumber).user(user).customerName(customerName).customerEmail(customerEmail).shippingAddress(shippingAddress).status(OrderStatus.CONFIRMED).items(new ArrayList<>()).build();

        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();

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

        return OrderResponse.fromEntity(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByTrackingNumber(String trackingNumber) {
        Optional<Order> orderOptional = orderRepository.findByTrackingNumberWithItems(trackingNumber);
        if (orderOptional.isEmpty()) {
            throw new ResourceNotFoundException("Order not found with tracking number: " + trackingNumber);
        }
        return OrderResponse.fromEntity(orderOptional.get());
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getOrdersByCustomerEmail(String customerEmail, Pageable pageable) {
        Page<Order> orderPage = orderRepository.findByCustomerEmail(customerEmail, pageable);
        List<OrderResponse> content = new ArrayList<>();

        for (Order order : orderPage.getContent()) {
            content.add(OrderResponse.fromEntity(order));
        }

        return new PagedResponse<>(content, orderPage.getNumber(), orderPage.getSize(), orderPage.getTotalElements(), orderPage.getTotalPages(), orderPage.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getMyOrders(User user, Pageable pageable) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null when fetching order history.");
        }
        Page<Order> orderPage = orderRepository.findByUserId(user.getId(), pageable);
        List<OrderResponse> content = new ArrayList<>();

        for (Order order : orderPage.getContent()) {
            content.add(OrderResponse.fromEntity(order));
        }

        return new PagedResponse<>(content, orderPage.getNumber(), orderPage.getSize(), orderPage.getTotalElements(), orderPage.getTotalPages(), orderPage.isLast());
    }

    @Override
    public OrderResponse updateOrderStatus(String trackingNumber, OrderStatus newStatus) {
        Order order = orderRepository.findByTrackingNumberWithItems(trackingNumber).orElseThrow(() -> new ResourceNotFoundException("Order not found with tracking number: " + trackingNumber));

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
                    throw new BadRequestException("Only PENDING orders can be moved to CONFIRMED");
                }
            }
            case SHIPPED -> {
                if (currentStatus != OrderStatus.CONFIRMED) {
                    throw new BadRequestException("Only CONFIRMED orders can be dispatched to SHIPPED");
                }
            }
            case DELIVERED -> {
                if (currentStatus != OrderStatus.SHIPPED) {
                    throw new BadRequestException("Only SHIPPED orders can be marked as DELIVERED");
                }
            }
            case CANCELLED -> {
                // Return stock back to inventory if order is cancelled
                for (OrderItem item : order.getItems()) {
                    Product product = item.getProduct();
                    product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                    productRepository.save(product);
                }
            }
            default -> throw new BadRequestException("Unsupported status transition to: " + newStatus);
        }

        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);
        return OrderResponse.fromEntity(updatedOrder);
    }

    /**
     * Resolves the cart to checkout, prioritizing the authenticated user's cart.
     */
    private Cart resolveCartForCheckout(User user, String cartToken) {
        if (user != null) {
            Optional<Cart> userCart = cartRepository.findByUserIdWithItems(user.getId());
            if (userCart.isPresent()) {
                return userCart.get();
            }
        }

        if (cartToken != null && !cartToken.trim().isEmpty()) {
            Optional<Cart> tokenCart = cartRepository.findByCartTokenWithItems(cartToken);
            if (tokenCart.isPresent()) {
                return tokenCart.get();
            }
        }

        throw new ResourceNotFoundException("No active shopping cart found for checkout.");
    }
}

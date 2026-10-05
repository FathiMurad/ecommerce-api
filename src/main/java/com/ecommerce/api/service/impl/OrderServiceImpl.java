package com.ecommerce.api.service.impl;

import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.response.OrderItemResponse;
import com.ecommerce.api.dto.response.OrderResponse;
import com.ecommerce.api.dto.response.PagedResponse;
import com.ecommerce.api.entity.Cart;
import com.ecommerce.api.entity.CartItem;
import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.OrderItem;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.enums.OrderStatus;
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
 * Implementation of OrderService providing atomic checkout execution and order management.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    @Override
    public OrderResponse checkout(String cartToken, CheckoutRequest request) {
        if (cartToken == null || cartToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Cart token is required for checkout.");
        }

        Optional<Cart> cartOptional = cartRepository.findByCartTokenWithItems(cartToken);
        if (cartOptional.isEmpty()) {
            throw new ResourceNotFoundException("Cart not found with token: " + cartToken);
        }

        Cart cart = cartOptional.get();
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new EmptyCartException("Cannot checkout with an empty cart.");
        }

        String trackingNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Order order = Order.builder().trackingNumber(trackingNumber).customerName(request.customerName()).customerEmail(request.customerEmail()).shippingAddress(request.shippingAddress()).status(OrderStatus.CONFIRMED).items(new ArrayList<>()).build();

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
}

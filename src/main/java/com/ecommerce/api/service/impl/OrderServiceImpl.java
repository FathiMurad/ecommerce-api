package com.ecommerce.api.service.impl;

import com.ecommerce.api.constant.CacheNames;
import com.ecommerce.api.constant.MoneyConstants;
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
import com.ecommerce.api.event.OrderPlacedEvent;
import com.ecommerce.api.exception.BadRequestException;
import com.ecommerce.api.exception.EmptyCartException;
import com.ecommerce.api.exception.InsufficientStockException;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.CartRepository;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.security.OrderAccessPolicy;
import com.ecommerce.api.service.OrderService;
import com.ecommerce.api.util.Emails;
import com.ecommerce.api.util.PageRequests;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Service implementation for managing orders and checkout operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private static final String TRACKING_NUMBER_PREFIX = "ORD-";
    private static final int TRACKING_NUMBER_RANDOM_LENGTH = 10;
    private static final Set<String> ALLOWED_ORDER_SORT_PROPERTIES = Set.of("id", "totalAmount", "status");

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final OrderAccessPolicy orderAccessPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    private record CustomerDetails(String name, String email, String address) {
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.PRODUCTS, allEntries = true)
    public OrderResponse checkout(User user, String cartToken, CheckoutRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Checkout request cannot be null.");
        }

        CustomerDetails details = resolveCustomerDetails(user, request);

        Cart cart = resolveCartForCheckout(user, cartToken);
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new EmptyCartException("Cannot checkout with an empty cart.");
        }

        Order order = buildInitialOrder(user, details.name(), details.email(), details.address());
        deductStockAndCreateOrderItems(order, cart);

        order.setTotalAmount(order.calculateTotalAmount().setScale(MoneyConstants.SCALE, RoundingMode.HALF_UP));
        Order savedOrder = orderRepository.save(order);

        cart.clear();
        cartRepository.save(cart);

        notifyOrderPlaced(savedOrder);

        return OrderResponse.fromEntity(savedOrder);
    }

    @Override
    public OrderResponse getOrderByTrackingNumber(String trackingNumber, User viewer) {
        if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Tracking number cannot be null or empty.");
        }

        Order order = orderRepository.getByTrackingNumberOrThrow(trackingNumber.trim());
        orderAccessPolicy.assertCanAccess(order, viewer);

        return OrderResponse.fromEntity(order);
    }

    @Override
    public PagedResponse<OrderResponse> getOrdersByCustomerEmail(String customerEmail, Pageable pageable) {
        if (customerEmail == null || customerEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer email cannot be null or empty.");
        }

        String normalizedEmail = Emails.normalize(customerEmail);
        Pageable safePageable = PageRequests.safe(pageable, ALLOWED_ORDER_SORT_PROPERTIES, "id");
        Page<Order> orderPage = orderRepository.findByCustomerEmailIgnoreCase(normalizedEmail, safePageable);
        return PagedResponse.from(orderPage, OrderResponse::fromEntity);
    }

    @Override
    public PagedResponse<OrderResponse> getMyOrders(User user, Pageable pageable) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User cannot be null when fetching order history.");
        }

        Pageable safePageable = PageRequests.safe(pageable, ALLOWED_ORDER_SORT_PROPERTIES, "id");
        Page<Order> orderPage = orderRepository.findByUserId(user.getId(), safePageable);
        return PagedResponse.from(orderPage, OrderResponse::fromEntity);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.PRODUCTS, allEntries = true)
    public OrderResponse updateOrderStatus(String trackingNumber, OrderStatus newStatus) {
        if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Tracking number cannot be null or empty.");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("New status cannot be null.");
        }

        Order order = orderRepository.getByTrackingNumberOrThrow(trackingNumber.trim());
        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == newStatus) {
            return OrderResponse.fromEntity(order);
        }

        if (currentStatus.isTerminal()) {
            throw new BadRequestException("Cannot modify an order already in terminal status: " + currentStatus);
        }

        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new BadRequestException("Cannot transition order from " + currentStatus + " to " + newStatus);
        }

        if (newStatus == OrderStatus.CONFIRMED) {
            meterRegistry.counter("ecommerce.orders.confirmed").increment();
        } else if (newStatus == OrderStatus.CANCELLED) {
            meterRegistry.counter("ecommerce.orders.cancelled").increment();
            restoreStockForOrder(order);
        }

        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);
        log.info("Order status updated for trackingNumber={}: {} -> {}", trackingNumber, currentStatus, newStatus);

        return OrderResponse.fromEntity(updatedOrder);
    }

    private CustomerDetails resolveCustomerDetails(User user, CheckoutRequest request) {
        String email = (user != null && user.getEmail() != null && !user.getEmail().trim().isEmpty()) ? user.getEmail() : (request != null ? request.customerEmail() : null);

        String name = null;
        if (user != null) {
            String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
            String last = user.getLastName() != null ? user.getLastName().trim() : "";
            String full = (first + " " + last).trim();
            if (!full.isEmpty()) {
                name = full;
            }
        }
        if (name == null && request != null) {
            name = request.customerName();
        }

        String address = (request != null) ? request.shippingAddress() : null;

        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer email is required for checkout.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name is required for checkout.");
        }
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Shipping address is required for checkout.");
        }

        return new CustomerDetails(name.trim(), Emails.normalize(email), address.trim());
    }

    private Order buildInitialOrder(User user, String customerName, String customerEmail, String shippingAddress) {
        String trackingNumber = generateUniqueTrackingNumber();

        return Order.builder().trackingNumber(trackingNumber).user(user).customerName(customerName).customerEmail(customerEmail).shippingAddress(shippingAddress).status(OrderStatus.PENDING).items(new ArrayList<>()).build();
    }

    private void deductStockAndCreateOrderItems(Order order, Cart cart) {
        List<CartItem> sortedItems = sortItemsByProductId(cart.getItems(), item -> item.getProduct().getId());

        for (CartItem cartItem : sortedItems) {
            Long productId = cartItem.getProduct().getId();
            Product product = productRepository.getForUpdateOrThrow(productId);

            if (product.getStockQuantity() < cartItem.getQuantity()) {
                log.warn("Insufficient stock during checkout for product id={}: requested={}, available={}", productId, cartItem.getQuantity(), product.getStockQuantity());
                throw new InsufficientStockException("Insufficient stock for product '" + product.getName() + "'. Available: " + product.getStockQuantity() + ", Requested: " + cartItem.getQuantity());
            }

            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            productRepository.save(product);

            OrderItem orderItem = OrderItem.builder().product(product).quantity(cartItem.getQuantity()).priceAtPurchase(product.getPrice()).build();
            order.addItem(orderItem);
        }
    }

    private void notifyOrderPlaced(Order order) {
        log.info("Order created successfully with trackingNumber={} for customerEmail={}, totalAmount={}, status={}", order.getTrackingNumber(), order.getCustomerEmail(), order.getTotalAmount(), order.getStatus());
        meterRegistry.counter("ecommerce.orders.created", "status", "pending").increment();
        eventPublisher.publishEvent(new OrderPlacedEvent(order.getId(), order.getTrackingNumber(), order.getCustomerEmail(), order.getCustomerName(), order.getTotalAmount()));
    }

    private void restoreStockForOrder(Order order) {
        List<OrderItem> sortedOrderItems = sortItemsByProductId(order.getItems(), item -> item.getProduct().getId());
        for (OrderItem item : sortedOrderItems) {
            Long productId = item.getProduct().getId();
            Product product = productRepository.getForUpdateOrThrow(productId);
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
            log.info("Restored stock for product id={} by quantity={} from cancelled order trackingNumber={}", productId, item.getQuantity(), order.getTrackingNumber());
        }
    }

    private <T> List<T> sortItemsByProductId(List<T> items, Function<T, Long> productIdExtractor) {
        List<T> sortedList = new ArrayList<>(items);
        sortedList.sort(Comparator.comparingLong(productIdExtractor::apply));
        return sortedList;
    }

    private Cart resolveCartForCheckout(User user, String cartToken) {
        if (user != null) {
            return cartRepository.findByUserIdWithItems(user.getId()).orElseGet(() -> resolveGuestOrThrow(cartToken, user.getId()));
        }

        if (cartToken != null && !cartToken.trim().isEmpty()) {
            Cart cart = cartRepository.findByCartTokenWithItems(cartToken.trim()).orElseThrow(() -> new ResourceNotFoundException("No active shopping cart found for checkout."));
            if (cart.getUser() != null) {
                log.warn("Guest attempted to checkout authenticated user cart via token: {}", cartToken);
                throw new BadRequestException("The specified cart belongs to a registered user. Please log in to complete checkout.");
            }
            return cart;
        }

        throw new ResourceNotFoundException("No active shopping cart found for checkout.");
    }

    private Cart resolveGuestOrThrow(String cartToken, Long userId) {
        if (cartToken != null && !cartToken.trim().isEmpty()) {
            return cartRepository.findByCartTokenWithItems(cartToken.trim()).filter(c -> c.getUser() == null || c.getUser().getId().equals(userId)).orElseThrow(() -> new ResourceNotFoundException("No active shopping cart found for user."));
        }
        throw new ResourceNotFoundException("No active shopping cart found for user.");
    }

    private String generateUniqueTrackingNumber() {
        String trackingNumber;
        do {
            trackingNumber = TRACKING_NUMBER_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, TRACKING_NUMBER_RANDOM_LENGTH).toUpperCase();
        } while (orderRepository.existsByTrackingNumber(trackingNumber));

        return trackingNumber;
    }
}

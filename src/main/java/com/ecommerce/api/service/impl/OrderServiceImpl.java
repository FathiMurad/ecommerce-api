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
import com.ecommerce.api.exception.ConflictException;
import com.ecommerce.api.exception.EmptyCartException;
import com.ecommerce.api.exception.InsufficientStockException;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.security.OrderAccessPolicy;
import com.ecommerce.api.service.CartResolver;
import com.ecommerce.api.service.OrderService;
import com.ecommerce.api.util.Emails;
import com.ecommerce.api.util.PageRequests;
import com.ecommerce.api.util.PublicIdGenerator;
import com.ecommerce.api.util.SortFields;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Service implementation for managing orders and checkout operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartResolver cartResolver;
    private final ProductRepository productRepository;
    private final OrderAccessPolicy orderAccessPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    private record CustomerDetails(String name, String email, String address) {
    }

    @Override
    @Transactional
    public OrderResponse checkout(User user, String cartToken, CheckoutRequest request) {
        Objects.requireNonNull(request, "Checkout request cannot be null.");

        CustomerDetails details = resolveCustomerDetails(user, request);

        Cart cart = resolveCartForCheckout(user, cartToken);
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new EmptyCartException("Cannot checkout with an empty cart.");
        }

        Order order = buildInitialOrder(user, details);
        deductStockAndCreateOrderItems(order, cart);

        order.setTotalAmount(order.calculateTotalAmount().setScale(MoneyConstants.SCALE, RoundingMode.HALF_UP));
        Order savedOrder = orderRepository.save(order);

        cart.clear();
        notifyOrderPlaced(savedOrder);

        return OrderResponse.fromEntity(savedOrder);
    }

    @Override
    public OrderResponse getOrderByTrackingNumber(String trackingNumber, User viewer) {
        Objects.requireNonNull(trackingNumber, "Tracking number cannot be null.");

        Order order = orderRepository.getByTrackingNumberOrThrow(trackingNumber.trim());
        orderAccessPolicy.assertCanAccess(order, viewer);

        return OrderResponse.fromEntity(order);
    }

    @Override
    public PagedResponse<OrderResponse> getOrdersByCustomerEmail(String customerEmail, Pageable pageable) {
        Objects.requireNonNull(customerEmail, "Customer email cannot be null.");

        String normalizedEmail = Emails.normalize(customerEmail);
        Pageable safePageable = PageRequests.safe(pageable, SortFields.ORDER_SORT_FIELDS, "id");
        Page<Order> orderPage = orderRepository.findByCustomerEmailIgnoreCase(normalizedEmail, safePageable);
        return PagedResponse.from(orderPage, OrderResponse::fromEntity);
    }

    @Override
    public PagedResponse<OrderResponse> getMyOrders(User user, Pageable pageable) {
        Objects.requireNonNull(user, "User cannot be null when fetching order history.");
        Objects.requireNonNull(user.getId(), "User ID cannot be null when fetching order history.");

        Pageable safePageable = PageRequests.safe(pageable, SortFields.ORDER_SORT_FIELDS, "id");
        Page<Order> orderPage = orderRepository.findByUserId(user.getId(), safePageable);
        return PagedResponse.from(orderPage, OrderResponse::fromEntity);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.PRODUCTS, allEntries = true, condition = "#newStatus == T(com.ecommerce.api.entity.enums.OrderStatus).CANCELLED")
    public OrderResponse updateOrderStatus(String trackingNumber, OrderStatus newStatus) {
        Objects.requireNonNull(trackingNumber, "Tracking number cannot be null.");
        Objects.requireNonNull(newStatus, "New status cannot be null.");

        Order order = orderRepository.getByTrackingNumberOrThrow(trackingNumber.trim());
        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == newStatus) {
            return OrderResponse.fromEntity(order);
        }

        if (currentStatus.isTerminal()) {
            throw new ConflictException("Cannot modify an order already in terminal status: " + currentStatus);
        }

        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new ConflictException("Cannot transition order from " + currentStatus + " to " + newStatus);
        }

        if (newStatus == OrderStatus.CANCELLED) {
            restoreStockForOrder(order);
        }

        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);

        if (newStatus == OrderStatus.CONFIRMED) {
            afterCommit(() -> meterRegistry.counter("ecommerce.orders.confirmed").increment());
        } else if (newStatus == OrderStatus.CANCELLED) {
            afterCommit(() -> meterRegistry.counter("ecommerce.orders.cancelled").increment());
        }

        log.info("Order status updated for trackingNumber={}: {} -> {}", trackingNumber, currentStatus, newStatus);
        return OrderResponse.fromEntity(updatedOrder);
    }

    private CustomerDetails resolveCustomerDetails(User user, CheckoutRequest request) {
        String email = firstNonBlank(user != null ? user.getEmail() : null, request.customerEmail());
        String name = firstNonBlank(fullName(user), request.customerName());
        String address = firstNonBlank(request.shippingAddress());

        if (!StringUtils.hasText(email)) {
            throw new BadRequestException("Customer email is required for checkout.");
        }
        if (!StringUtils.hasText(name)) {
            throw new BadRequestException("Customer name is required for checkout.");
        }
        if (!StringUtils.hasText(address)) {
            throw new BadRequestException("Shipping address is required for checkout.");
        }

        return new CustomerDetails(name, Emails.normalize(email), address);
    }

    private String fullName(User user) {
        if (user == null) {
            return null;
        }
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last = user.getLastName() != null ? user.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? null : full;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Order buildInitialOrder(User user, CustomerDetails details) {
        return Order.builder().trackingNumber(PublicIdGenerator.generateTrackingNumber()).user(user).customerName(details.name()).customerEmail(details.email()).shippingAddress(details.address()).status(OrderStatus.PENDING).items(new ArrayList<>()).build();
    }

    private void deductStockAndCreateOrderItems(Order order, Cart cart) {
        List<CartItem> sortedItems = sortItemsByProductId(cart.getItems(), item -> item.getProduct().getId());

        for (CartItem cartItem : sortedItems) {
            Long productId = cartItem.getProduct().getId();
            Product product = productRepository.getForUpdateOrThrow(productId);

            if (product.getStockQuantity() < cartItem.getQuantity()) {
                log.warn("Insufficient stock during checkout for product id={}: requested={}, available={}", productId, cartItem.getQuantity(), product.getStockQuantity());
                throw InsufficientStockException.of(product, cartItem.getQuantity());
            }

            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());

            OrderItem orderItem = OrderItem.builder().product(product).quantity(cartItem.getQuantity()).priceAtPurchase(product.getPrice()).build();
            order.addItem(orderItem);
        }
    }

    private void notifyOrderPlaced(Order order) {
        log.info("Order created successfully: orderId={}, trackingNumber={}, totalAmount={}, status={}", order.getId(), order.getTrackingNumber(), order.getTotalAmount(), order.getStatus());
        afterCommit(() -> meterRegistry.counter("ecommerce.orders.created", "status", "pending").increment());
        eventPublisher.publishEvent(new OrderPlacedEvent(order.getId(), order.getTrackingNumber(), order.getCustomerEmail(), order.getCustomerName(), order.getTotalAmount()));
    }

    private void restoreStockForOrder(Order order) {
        List<OrderItem> sortedOrderItems = sortItemsByProductId(order.getItems(), item -> item.getProduct().getId());
        for (OrderItem item : sortedOrderItems) {
            Long productId = item.getProduct().getId();
            Product product = productRepository.getForUpdateOrThrow(productId);
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
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
            return cartResolver.findForUser(user, cartToken).orElseThrow(() -> new ResourceNotFoundException("No active shopping cart found for user."));
        }
        return cartResolver.findGuest(cartToken).orElseThrow(() -> new ResourceNotFoundException("No active shopping cart found for checkout."));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}

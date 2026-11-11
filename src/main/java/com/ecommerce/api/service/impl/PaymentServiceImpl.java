package com.ecommerce.api.service.impl;

import com.ecommerce.api.constant.CacheNames;
import com.ecommerce.api.constant.MoneyConstants;
import com.ecommerce.api.dto.request.PaymentRequest;
import com.ecommerce.api.dto.response.PaymentResponse;
import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.Payment;
import com.ecommerce.api.entity.enums.OrderStatus;
import com.ecommerce.api.entity.enums.PaymentStatus;
import com.ecommerce.api.entity.enums.Role;
import com.ecommerce.api.exception.BadRequestException;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.repository.PaymentRepository;
import com.ecommerce.api.service.OrderService;
import com.ecommerce.api.service.PaymentService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

/**
 * Service implementation for managing payment transactions using a simulated payment gateway.
 * <p>
 * Simulates payment processing behavior for development and testing:
 * - Cards ending in 0000 simulate bank decline (insufficient funds), cancelling the order and restoring stock.
 * - All other cards simulate approved payment, moving the order to CONFIRMED.
 * <p>
 * Enforces ownership checks to prevent IDOR and maintains idempotency against duplicate charges.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String TRANSACTION_ID_PREFIX = "TXN-";
    private static final int TRANSACTION_ID_RANDOM_LENGTH = 10;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.PRODUCTS, allEntries = true)
    public PaymentResponse processPayment(String trackingNumber, PaymentRequest request) {
        validateProcessPaymentRequest(trackingNumber, request);

        Order order = findOrderByTrackingNumberOrThrow(trackingNumber.trim());
        validateOrderAccess(order);
        validateOrderPayable(order, request);

        Payment payment = resolvePayment(order);
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            throw new BadRequestException("Order is already paid with transaction ID: " + payment.getTransactionId());
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        String transactionId = generateUniqueTransactionId();
        BigDecimal chargeAmount = (request.getAmount() != null ? request.getAmount() : order.getTotalAmount()).setScale(MoneyConstants.SCALE, RoundingMode.HALF_UP);

        payment.setTransactionId(transactionId);
        payment.setAmount(chargeAmount);
        payment.setPaymentMethod(request.getPaymentMethod());

        boolean isDeclined = request.getCardNumber() != null && request.getCardNumber().endsWith("0000");
        if (isDeclined) {
            return handleDeclinedPayment(payment, trackingNumber, transactionId, sample);
        }

        return handleApprovedPayment(payment, trackingNumber, transactionId, sample);
    }

    private PaymentResponse handleDeclinedPayment(Payment payment, String trackingNumber, String transactionId, Timer.Sample sample) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason("Card declined by issuing bank (insufficient funds / test decline)");

        Payment savedPayment = paymentRepository.save(payment);
        orderService.updateOrderStatus(trackingNumber, OrderStatus.CANCELLED);

        sample.stop(meterRegistry.timer("ecommerce.payment.processing.time", "outcome", "declined"));
        meterRegistry.counter("ecommerce.payments.processed", "outcome", "declined").increment();

        log.warn("Payment declined for trackingNumber={}, transactionId={}", trackingNumber, transactionId);

        return PaymentResponse.fromEntity(savedPayment);
    }

    private PaymentResponse handleApprovedPayment(Payment payment, String trackingNumber, String transactionId, Timer.Sample sample) {
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setFailureReason(null);

        Payment savedPayment = paymentRepository.save(payment);
        orderService.updateOrderStatus(trackingNumber, OrderStatus.CONFIRMED);

        sample.stop(meterRegistry.timer("ecommerce.payment.processing.time", "outcome", "approved"));
        meterRegistry.counter("ecommerce.payments.processed", "outcome", "approved").increment();

        log.info("Payment completed successfully for trackingNumber={}, transactionId={}, amount={}", trackingNumber, transactionId, payment.getAmount());

        return PaymentResponse.fromEntity(savedPayment);
    }

    private void validateProcessPaymentRequest(String trackingNumber, PaymentRequest request) {
        if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Tracking number cannot be null or empty.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Payment request cannot be null.");
        }
        if (request.getPaymentMethod() == null) {
            throw new IllegalArgumentException("Payment method is required.");
        }
    }

    private void validateOrderPayable(Order order, PaymentRequest request) {
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Cannot process payment for an order with status: " + order.getStatus());
        }

        if (request.getAmount() != null && request.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new BadRequestException("Payment amount (" + request.getAmount() + ") does not match order total (" + order.getTotalAmount() + ").");
        }
    }

    private Payment resolvePayment(Order order) {
        return paymentRepository.findByOrderId(order.getId()).orElseGet(() -> Payment.builder().order(order).build());
    }

    private void validateOrderAccess(Order order) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName());

        if (order.getUser() != null) {
            if (!isAuthenticated) {
                throw new AccessDeniedException("Authentication required to pay for this registered user order.");
            }

            String currentUserEmail = authentication.getName();
            boolean isAdmin = hasAdminRole(authentication);

            if (!isAdmin && !currentUserEmail.equalsIgnoreCase(order.getUser().getEmail())) {
                throw new AccessDeniedException("You are not authorized to pay for this order.");
            }
        }
    }

    private boolean hasAdminRole(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (Role.ROLE_ADMIN.name().equals(authority.getAuthority())) {
                return true;
            }
        }

        return false;
    }

    private Order findOrderByTrackingNumberOrThrow(String trackingNumber) {
        Optional<Order> order = orderRepository.findByTrackingNumberWithItems(trackingNumber);
        if (order.isEmpty()) {
            throw new ResourceNotFoundException("Order not found with tracking number: " + trackingNumber);
        }

        return order.get();
    }

    private String generateUniqueTransactionId() {
        String transactionId;
        do {
            transactionId = TRANSACTION_ID_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, TRANSACTION_ID_RANDOM_LENGTH).toUpperCase();
        } while (paymentRepository.existsByTransactionId(transactionId));

        return transactionId;
    }
}

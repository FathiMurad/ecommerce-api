package com.ecommerce.api.service.impl;

import com.ecommerce.api.constant.CacheNames;
import com.ecommerce.api.constant.MoneyConstants;
import com.ecommerce.api.dto.request.PaymentRequest;
import com.ecommerce.api.dto.response.PaymentResponse;
import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.Payment;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.entity.enums.OrderStatus;
import com.ecommerce.api.entity.enums.PaymentStatus;
import com.ecommerce.api.exception.BadRequestException;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.repository.PaymentRepository;
import com.ecommerce.api.repository.UserRepository;
import com.ecommerce.api.security.OrderAccessPolicy;
import com.ecommerce.api.service.OrderService;
import com.ecommerce.api.service.PaymentService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Service implementation for managing payment transactions using a simulated payment gateway.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private static final String TRANSACTION_ID_PREFIX = "TXN-";
    private static final int TRANSACTION_ID_RANDOM_LENGTH = 10;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final OrderAccessPolicy orderAccessPolicy;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.PRODUCTS, allEntries = true)
    public PaymentResponse processPayment(String trackingNumber, PaymentRequest request) {
        Assert.hasText(trackingNumber, "Tracking number cannot be null or empty.");
        Objects.requireNonNull(request, "Payment request cannot be null.");
        Objects.requireNonNull(request.getPaymentMethod(), "Payment method is required.");

        Order order = orderRepository.getByTrackingNumberOrThrow(trackingNumber.trim());

        User currentUser = resolveAuthenticatedUser();
        orderAccessPolicy.assertCanAccess(order, currentUser);
        validateOrderPayable(order, request);

        Payment payment = resolvePayment(order);
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            throw new BadRequestException("Order is already paid with transaction ID: " + payment.getTransactionId());
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "error";
        try {
            String transactionId = generateUniqueTransactionId();
            BigDecimal chargeAmount = (request.getAmount() != null ? request.getAmount() : order.getTotalAmount()).setScale(MoneyConstants.SCALE, RoundingMode.HALF_UP);

            payment.setTransactionId(transactionId);
            payment.setAmount(chargeAmount);
            payment.setPaymentMethod(request.getPaymentMethod());

            boolean isDeclined = request.getCardNumber() != null && request.getCardNumber().endsWith("0000");
            if (isDeclined) {
                PaymentResponse response = handleDeclinedPayment(payment, trackingNumber, transactionId);
                outcome = "declined";
                return response;
            }

            PaymentResponse response = handleApprovedPayment(payment, trackingNumber, transactionId);
            outcome = "approved";
            return response;
        } finally {
            sample.stop(meterRegistry.timer("ecommerce.payment.processing.time", "outcome", outcome));
            meterRegistry.counter("ecommerce.payments.processed", "outcome", outcome).increment();
        }
    }

    private PaymentResponse handleDeclinedPayment(Payment payment, String trackingNumber, String transactionId) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason("Card declined by issuing bank (insufficient funds / test decline)");

        Payment savedPayment = paymentRepository.save(payment);
        orderService.updateOrderStatus(trackingNumber, OrderStatus.CANCELLED);

        log.warn("Payment declined for trackingNumber={}, transactionId={}", trackingNumber, transactionId);
        return PaymentResponse.fromEntity(savedPayment);
    }

    private PaymentResponse handleApprovedPayment(Payment payment, String trackingNumber, String transactionId) {
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setFailureReason(null);

        Payment savedPayment = paymentRepository.save(payment);
        orderService.updateOrderStatus(trackingNumber, OrderStatus.CONFIRMED);

        log.info("Payment completed successfully for trackingNumber={}, transactionId={}, amount={}", trackingNumber, transactionId, payment.getAmount());

        return PaymentResponse.fromEntity(savedPayment);
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

    private User resolveAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }

    private String generateUniqueTransactionId() {
        String transactionId;
        do {
            transactionId = TRANSACTION_ID_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, TRANSACTION_ID_RANDOM_LENGTH).toUpperCase();
        } while (paymentRepository.existsByTransactionId(transactionId));

        return transactionId;
    }
}

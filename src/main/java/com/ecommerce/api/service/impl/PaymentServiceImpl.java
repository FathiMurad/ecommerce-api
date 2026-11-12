package com.ecommerce.api.service.impl;

import com.ecommerce.api.constant.MoneyConstants;
import com.ecommerce.api.dto.request.PaymentRequest;
import com.ecommerce.api.dto.response.PaymentResponse;
import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.Payment;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.entity.enums.OrderStatus;
import com.ecommerce.api.entity.enums.PaymentStatus;
import com.ecommerce.api.exception.BusinessRuleViolationException;
import com.ecommerce.api.exception.ConflictException;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.repository.PaymentRepository;
import com.ecommerce.api.security.OrderAccessPolicy;
import com.ecommerce.api.service.OrderService;
import com.ecommerce.api.service.PaymentService;
import com.ecommerce.api.util.PublicIdGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Service implementation for managing payment transactions using a simulated payment gateway.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final OrderAccessPolicy orderAccessPolicy;
    private final MeterRegistry meterRegistry;

    private record Outcome(PaymentStatus paymentStatus, OrderStatus orderStatusOrNull, String failureReason,
                           String metricLabel) {
    }

    @Override
    @Transactional
    public PaymentResponse processPayment(String trackingNumber, PaymentRequest request, User viewer) {
        Objects.requireNonNull(trackingNumber, "Tracking number cannot be null.");
        Objects.requireNonNull(request, "Payment request cannot be null.");
        Objects.requireNonNull(request.getPaymentMethod(), "Payment method is required.");

        Order order = orderRepository.getByTrackingNumberOrThrow(trackingNumber.trim());
        orderAccessPolicy.assertCanAccess(order, viewer);
        validateOrderPayable(order, request);

        Payment payment = resolvePayment(order);
        int currentFailures = payment.getFailedAttempts() != null ? payment.getFailedAttempts() : 0;
        if (currentFailures >= MAX_FAILED_ATTEMPTS) {
            throw new ConflictException("Maximum payment retry attempts (" + MAX_FAILED_ATTEMPTS + ") exceeded for order: " + order.getTrackingNumber());
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        String outcomeLabel = "error";
        try {
            BigDecimal chargeAmount = order.getTotalAmount().setScale(MoneyConstants.SCALE, RoundingMode.HALF_UP);

            payment.setTransactionId(PublicIdGenerator.generateTransactionId());
            payment.setAmount(chargeAmount);
            payment.setPaymentMethod(request.getPaymentMethod());

            boolean isDeclined = request.getCardNumber() != null && request.getCardNumber().endsWith("0000");
            Outcome outcome = isDeclined ? new Outcome(PaymentStatus.FAILED, null, "Card declined by issuing bank (insufficient funds / test decline)", "declined") : new Outcome(PaymentStatus.COMPLETED, OrderStatus.CONFIRMED, null, "approved");

            PaymentResponse response = finalizePayment(payment, order, outcome, currentFailures);
            outcomeLabel = outcome.metricLabel();
            return response;
        } finally {
            sample.stop(meterRegistry.timer("ecommerce.payment.processing.time", "outcome", outcomeLabel));
            meterRegistry.counter("ecommerce.payments.processed", "outcome", outcomeLabel).increment();
        }
    }

    private PaymentResponse finalizePayment(Payment payment, Order order, Outcome outcome, int currentFailures) {
        payment.setStatus(outcome.paymentStatus());
        payment.setFailureReason(outcome.failureReason());

        if (outcome.paymentStatus() == PaymentStatus.FAILED) {
            payment.setFailedAttempts(currentFailures + 1);
            log.warn("Payment declined for trackingNumber={}, transactionId={}, attempt={}", order.getTrackingNumber(), payment.getTransactionId(), payment.getFailedAttempts());
        } else {
            log.info("Payment completed successfully for trackingNumber={}, transactionId={}, amount={}", order.getTrackingNumber(), payment.getTransactionId(), payment.getAmount());
        }

        Payment savedPayment = paymentRepository.save(payment);

        if (outcome.orderStatusOrNull() != null) {
            orderService.updateOrderStatus(order.getTrackingNumber(), outcome.orderStatusOrNull());
        }

        return PaymentResponse.fromEntity(savedPayment);
    }

    private void validateOrderPayable(Order order, PaymentRequest request) {
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ConflictException("Cannot process payment for an order with status: " + order.getStatus());
        }

        if (request.getAmount() != null && request.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new BusinessRuleViolationException("Payment amount (" + request.getAmount() + ") does not match order total (" + order.getTotalAmount() + ").");
        }
    }

    private Payment resolvePayment(Order order) {
        return paymentRepository.findByOrderId(order.getId()).orElseGet(() -> Payment.builder().order(order).build());
    }
}

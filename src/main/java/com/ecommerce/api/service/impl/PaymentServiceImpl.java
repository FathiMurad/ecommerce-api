package com.ecommerce.api.service.impl;

import com.ecommerce.api.constant.MoneyConstants;
import com.ecommerce.api.dto.request.PaymentRequest;
import com.ecommerce.api.dto.response.PaymentResponse;
import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.OrderItem;
import com.ecommerce.api.entity.Payment;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.enums.OrderStatus;
import com.ecommerce.api.entity.enums.PaymentStatus;
import com.ecommerce.api.exception.BadRequestException;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.repository.PaymentRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Implementation of PaymentService providing idempotent payment execution,
 * stock restoration on failure, and role-aware order authorization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public PaymentResponse processPayment(String trackingNumber, PaymentRequest request) {
        if (trackingNumber == null || trackingNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Tracking number cannot be null or empty.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Payment request cannot be null.");
        }

        Order order = orderRepository.findByTrackingNumberWithItems(trackingNumber.trim()).orElseThrow(() -> new ResourceNotFoundException("Order not found with tracking number: " + trackingNumber));

        // Ownership verification (registered customer can only pay their own order; admin can pay any)
        validateOrderAccess(order);

        // State validation: only PENDING orders can be charged
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Cannot process payment for an order with status: " + order.getStatus());
        }

        // Validate amount precision and equality via compareTo if supplied in request
        if (request.getAmount() != null) {
            if (request.getAmount().compareTo(order.getTotalAmount()) != 0) {
                throw new BadRequestException("Payment amount (" + request.getAmount() + ") does not match order total (" + order.getTotalAmount() + ").");
            }
        }

        // Idempotency check: Reuse existing record or verify not already completed
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseGet(() -> Payment.builder().order(order).build());

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            throw new BadRequestException("Order is already paid with transaction ID: " + payment.getTransactionId());
        }

        // Simulate Gateway: Card ending in "0000" triggers simulated failure; safely handle null card numbers
        boolean isDeclined = request.getCardNumber() != null && request.getCardNumber().endsWith("0000");
        String transactionId = generateUniqueTransactionId();

        BigDecimal chargeAmount = (request.getAmount() != null ? request.getAmount() : order.getTotalAmount()).setScale(MoneyConstants.SCALE, RoundingMode.HALF_UP);

        payment.setTransactionId(transactionId);
        payment.setAmount(chargeAmount);
        payment.setPaymentMethod(request.getPaymentMethod());

        if (isDeclined) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Card declined by issuing bank (insufficient funds / test decline)");
            order.setStatus(OrderStatus.CANCELLED);

            // Rollback reserved stock
            restoreInventory(order);

            Payment savedPayment = paymentRepository.save(payment);
            orderRepository.save(order);

            log.warn("Payment declined for trackingNumber={}, transactionId={}", trackingNumber, transactionId);
            return PaymentResponse.fromEntity(savedPayment);
        }

        // Payment approved
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setFailureReason(null);
        order.setStatus(OrderStatus.CONFIRMED);

        Payment savedPayment = paymentRepository.save(payment);
        orderRepository.save(order);

        log.info("Payment completed successfully for trackingNumber={}, transactionId={}, amount={}", trackingNumber, transactionId, payment.getAmount());

        return PaymentResponse.fromEntity(savedPayment);
    }

    private void restoreInventory(Order order) {
        if (order.getItems() == null) {
            return;
        }
        for (OrderItem item : order.getItems()) {
            Long productId = item.getProduct().getId();
            Product product = productRepository.findById(productId).orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }
    }

    private void validateOrderAccess(Order order) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName());

        // If the order belongs to a registered user, enforce strict identity match or ADMIN role
        if (order.getUser() != null) {
            if (!isAuthenticated) {
                throw new AccessDeniedException("Authentication required to pay for this registered user order.");
            }

            String currentUserEmail = authentication.getName();
            boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (!isAdmin && !order.getUser().getEmail().equalsIgnoreCase(currentUserEmail)) {
                throw new AccessDeniedException("You are not authorized to pay for this order.");
            }
        }
        // If order.getUser() == null, it is an anonymous guest checkout order and payment is permitted via trackingNumber
    }

    private String generateUniqueTransactionId() {
        String transactionId;
        do {
            transactionId = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        } while (paymentRepository.existsByTransactionId(transactionId));
        return transactionId;
    }
}

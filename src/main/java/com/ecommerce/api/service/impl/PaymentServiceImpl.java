package com.ecommerce.api.service.impl;

import com.ecommerce.api.dto.request.PaymentRequest;
import com.ecommerce.api.dto.response.PaymentResponse;
import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.OrderItem;
import com.ecommerce.api.entity.enums.OrderStatus;
import com.ecommerce.api.entity.Payment;
import com.ecommerce.api.entity.enums.PaymentStatus;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.exception.BadRequestException;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.repository.PaymentRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.repository.UserRepository;
import com.ecommerce.api.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public PaymentResponse processPayment(String trackingNumber, PaymentRequest request) {
        Order order = orderRepository.findByTrackingNumber(trackingNumber).orElseThrow(() -> new ResourceNotFoundException("Order not found with tracking number: " + trackingNumber));

        // Ownership verification (customer can only pay their own order; admin can pay any)
        validateOrderAccess(order);

        // State validation
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot process payment for an order with status: " + order.getStatus());
        }

        paymentRepository.findByOrder(order).ifPresent(p -> {
            if (p.getStatus() == PaymentStatus.COMPLETED) {
                throw new BadRequestException("Order is already paid with transaction ID: " + p.getTransactionId());
            }
        });

        // Simulate Gateway: Card ending in "0000" triggers simulated failure
        boolean isDeclined = request.getCardNumber().endsWith("0000");

        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = Payment.builder().order(order).transactionId(transactionId).amount(order.getTotalAmount()).paymentMethod(request.getPaymentMethod()).build();

        if (isDeclined) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Card declined by issuing bank (insufficient funds / test decline)");
            order.setStatus(OrderStatus.CANCELLED);

            // Rollback reserved stock
            restoreInventory(order);

            paymentRepository.save(payment);
            orderRepository.save(order);

            return PaymentResponse.fromEntity(payment);
        }

        // Payment approved
        payment.setStatus(PaymentStatus.COMPLETED);
        order.setStatus(OrderStatus.CONFIRMED);

        paymentRepository.save(payment);
        orderRepository.save(order);

        return PaymentResponse.fromEntity(payment);
    }

    private void restoreInventory(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }
    }

    private void validateOrderAccess(Order order) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is unauthenticated");
        }

        String currentUserEmail = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && order.getUser() != null && !order.getUser().getEmail().equals(currentUserEmail)) {
            throw new AccessDeniedException("You are not authorized to pay for this order");
        }
    }
}

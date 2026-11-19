package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.PaymentRequest;
import com.ecommerce.api.dto.response.PaymentResponse;
import com.ecommerce.api.entity.User;

/**
 * Service interface defining payment processing operations for customer orders.
 */
public interface PaymentService {

    PaymentResponse processPayment(String trackingNumber, PaymentRequest request, User viewer);
}

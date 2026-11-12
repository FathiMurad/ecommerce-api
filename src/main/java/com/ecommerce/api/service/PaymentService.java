package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.PaymentRequest;
import com.ecommerce.api.dto.response.PaymentResponse;
import com.ecommerce.api.entity.User;

/**
 * Service for managing payment transactions.
 */
public interface PaymentService {

    PaymentResponse processPayment(String trackingNumber, PaymentRequest request, User viewer);
}

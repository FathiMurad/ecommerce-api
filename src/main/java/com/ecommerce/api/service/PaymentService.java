package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.PaymentRequest;
import com.ecommerce.api.dto.response.PaymentResponse;

/**
 * Service contract for payment transactions.
 */
public interface PaymentService {

    /**
     * Processes payment for a specific order.
     *
     * @param trackingNumber The unique tracking number of the order.
     * @param request        Credit card and billing details.
     * @return PaymentResponse containing transaction details.
     */
    PaymentResponse processPayment(String trackingNumber, PaymentRequest request);
}

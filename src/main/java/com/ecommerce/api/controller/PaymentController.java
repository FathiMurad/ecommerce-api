package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.PaymentRequest;
import com.ecommerce.api.dto.response.PaymentResponse;
import com.ecommerce.api.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller handling customer payment operations.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/orders/{trackingNumber}")
    public ResponseEntity<PaymentResponse> chargeOrder(@PathVariable String trackingNumber, @Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.processPayment(trackingNumber, request);
        return ResponseEntity.ok(response);
    }
}

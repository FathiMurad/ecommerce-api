package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.PaymentRequest;
import com.ecommerce.api.dto.response.PaymentResponse;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.security.SecurityUser;
import com.ecommerce.api.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller handling customer payment operations and transaction settlement
 * for both registered customer orders and guest checkouts.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Endpoints for order payment processing, payment gateway interaction, and settlement")
class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Charge and settle order payment", description = "Validates payment credentials, interacts with the payment provider, creates an idempotent transaction record, and advances the order status to CONFIRMED.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Payment processed", content = @Content(schema = @Schema(implementation = PaymentResponse.class))), @ApiResponse(responseCode = "400", description = "Invalid payment payload", content = @Content), @ApiResponse(responseCode = "404", description = "Order not found with the specified tracking number", content = @Content), @ApiResponse(responseCode = "409", description = "Order is not payable or retry limit exceeded", content = @Content), @ApiResponse(responseCode = "422", description = "Payment amount does not match order total", content = @Content)})
    @PostMapping("/orders/{trackingNumber}")
    public ResponseEntity<PaymentResponse> chargeOrder(@AuthenticationPrincipal SecurityUser securityUser, @Parameter(description = "Order unique tracking number", required = true) @PathVariable String trackingNumber, @Valid @RequestBody PaymentRequest request) {
        User viewer = ControllerUtils.extractUser(securityUser);
        PaymentResponse response = paymentService.processPayment(trackingNumber, request, viewer);
        return ResponseEntity.ok(response);
    }
}

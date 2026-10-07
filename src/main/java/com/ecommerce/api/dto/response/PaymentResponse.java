package com.ecommerce.api.dto.response;

import com.ecommerce.api.entity.Payment;
import com.ecommerce.api.entity.enums.PaymentMethod;
import com.ecommerce.api.entity.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO returned after transaction execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long paymentId;
    private String transactionId;
    private String orderTrackingNumber;
    private BigDecimal amount;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private String failureReason;
    private LocalDateTime timestamp;

    public static PaymentResponse fromEntity(Payment payment) {
        return PaymentResponse.builder().paymentId(payment.getId()).transactionId(payment.getTransactionId()).orderTrackingNumber(payment.getOrder().getTrackingNumber()).amount(payment.getAmount()).status(payment.getStatus()).paymentMethod(payment.getPaymentMethod()).failureReason(payment.getFailureReason()).timestamp(payment.getCreatedAt()).build();
    }
}

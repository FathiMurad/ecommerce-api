package com.ecommerce.api.dto.request;

import com.ecommerce.api.constant.MoneyConstants;
import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Payload sent by the client to charge an order.
 * Card fields are optional at DTO level to support CASH_ON_DELIVERY and external redirect methods,
 * while maintaining regex constraints whenever card data is supplied.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero")
    @Digits(integer = MoneyConstants.PRECISION - MoneyConstants.SCALE, fraction = MoneyConstants.SCALE, message = "Payment amount format is invalid")
    private BigDecimal amount;

    @Pattern(regexp = "^[0-9]{16}$", message = "Card number must be exactly 16 digits")
    private String cardNumber;

    @Size(max = Order.CUSTOMER_NAME_MAX_LENGTH, message = "Card holder name must not exceed {max} characters")
    private String cardHolderName;

    @Pattern(regexp = "^(0[1-9]|1[0-2])/[0-9]{2}$", message = "Expiry format must be MM/YY")
    private String expiryDate;

    @Pattern(regexp = "^[0-9]{3,4}$", message = "CVV must be 3 or 4 digits")
    private String cvv;
}

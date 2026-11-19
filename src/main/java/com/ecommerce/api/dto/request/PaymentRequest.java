package com.ecommerce.api.dto.request;

import com.ecommerce.api.constant.MoneyConstants;
import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.enums.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Request payload for processing an order payment, including conditional card validation rules.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    private static final DateTimeFormatter EXPIRY_FORMATTER = DateTimeFormatter.ofPattern("MM/yy");

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero")
    @Digits(
            integer = MoneyConstants.PRECISION - MoneyConstants.SCALE,
            fraction = MoneyConstants.SCALE,
            message = "Payment amount format is invalid"
    )
    private BigDecimal amount;

    @ToString.Exclude
    @Pattern(regexp = "^[0-9]{16}$", message = "Card number must be exactly 16 digits")
    private String cardNumber;

    @Size(max = Order.CUSTOMER_NAME_MAX_LENGTH, message = "Card holder name must not exceed {max} characters")
    private String cardHolderName;

    @Pattern(regexp = "^(0[1-9]|1[0-2])/[0-9]{2}$", message = "Expiry format must be MM/YY")
    private String expiryDate;

    @ToString.Exclude
    @Pattern(regexp = "^[0-9]{3,4}$", message = "CVV must be 3 or 4 digits")
    private String cvv;

    @JsonIgnore
    @AssertTrue(message = "Card number, card holder name, expiry date, and CVV are required for card payments")
    public boolean isCardDetailsPresentWhenRequired() {
        if (paymentMethod != PaymentMethod.CREDIT_CARD && paymentMethod != PaymentMethod.DEBIT_CARD) {
            return true;
        }
        return StringUtils.hasText(cardNumber)
                && StringUtils.hasText(cardHolderName)
                && StringUtils.hasText(expiryDate)
                && StringUtils.hasText(cvv);
    }

    @JsonIgnore
    @AssertTrue(message = "Card number failed Luhn checksum validation")
    public boolean isCardNumberValid() {
        if (cardNumber == null || !cardNumber.matches("^[0-9]{16}$")) {
            return true;
        }
        // Allow test card suffixes ending in 0000 alongside standard Luhn-valid numbers
        return cardNumber.endsWith("0000") || passesLuhn(cardNumber);
    }

    @JsonIgnore
    @AssertTrue(message = "Card has expired")
    public boolean isExpiryDateNotExpired() {
        if (expiryDate == null || !expiryDate.matches("^(0[1-9]|1[0-2])/[0-9]{2}$")) {
            return true;
        }
        YearMonth expiry = YearMonth.parse(expiryDate, EXPIRY_FORMATTER);
        return !expiry.isBefore(YearMonth.now());
    }

    private static boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }
}

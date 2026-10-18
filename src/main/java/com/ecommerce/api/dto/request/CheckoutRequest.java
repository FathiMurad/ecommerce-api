package com.ecommerce.api.dto.request;

import com.ecommerce.api.entity.Order;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload required to convert an active shopping cart into a confirmed order.
 */
public record CheckoutRequest(
        @NotBlank(message = "Customer name is mandatory") @Size(max = Order.CUSTOMER_NAME_MAX_LENGTH, message = "Customer name must not exceed {max} characters") String customerName,

        @NotBlank(message = "Customer email is mandatory") @Email(message = "Customer email format is invalid") @Size(max = Order.CUSTOMER_EMAIL_MAX_LENGTH, message = "Customer email must not exceed {max} characters") String customerEmail,

        @NotBlank(message = "Shipping address is mandatory") @Size(max = Order.SHIPPING_ADDRESS_MAX_LENGTH, message = "Shipping address must not exceed {max} characters") String shippingAddress) {
}

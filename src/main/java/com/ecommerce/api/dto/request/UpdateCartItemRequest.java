package com.ecommerce.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for updating an existing cart item quantity.
 */
public record UpdateCartItemRequest(
        @NotNull(message = "Quantity is mandatory") @Min(value = 1, message = "Quantity must be at least 1") Integer quantity) {
}

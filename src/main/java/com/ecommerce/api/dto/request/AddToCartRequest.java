package com.ecommerce.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for adding a product item and quantity to a shopping cart.
 */
public record AddToCartRequest(
        @NotNull(message = "Product ID is mandatory")
        Long productId,

        @NotNull(message = "Quantity is mandatory")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity
) {
}

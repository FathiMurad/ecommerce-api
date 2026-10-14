package com.ecommerce.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for adding a specific quantity of a product to a cart.
 */
public record AddToCartRequest(@NotNull(message = "Product ID is mandatory") Long productId,

                               @NotNull(message = "Quantity is mandatory") @Min(value = 1, message = "Quantity must be at least 1") Integer quantity) {
}

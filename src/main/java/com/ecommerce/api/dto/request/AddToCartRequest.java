package com.ecommerce.api.dto.request;

/**
 * Payload for adding a specific quantity of a product to a cart.
 */
public record AddToCartRequest(Long productId, Integer quantity) {
}

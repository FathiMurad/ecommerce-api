package com.ecommerce.api.dto.request;

/**
 * Payload for updating an existing cart item quantity.
 */
public record UpdateCartItemRequest(Integer quantity) {
}

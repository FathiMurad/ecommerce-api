package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.UpdateCartItemRequest;
import com.ecommerce.api.dto.response.CartResponse;

/**
 * Service contract for shopping cart operations.
 */
public interface CartService {

    /**
     * Retrieves an existing cart by token or creates a new empty cart if token is absent/invalid.
     */
    CartResponse getOrCreateCart(String cartToken);

    /**
     * Adds an item to the cart, verifying inventory availability.
     */
    CartResponse addToCart(String cartToken, AddToCartRequest request);

    /**
     * Updates an existing cart item's quantity.
     */
    CartResponse updateCartItem(String cartToken, Long productId, UpdateCartItemRequest request);

    /**
     * Removes an item completely from the cart.
     */
    CartResponse removeCartItem(String cartToken, Long productId);

    /**
     * Clears all items from the specified cart.
     */
    void clearCart(String cartToken);
}

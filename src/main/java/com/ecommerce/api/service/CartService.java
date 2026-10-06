package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.UpdateCartItemRequest;
import com.ecommerce.api.dto.response.CartResponse;
import com.ecommerce.api.entity.User;

/**
 * Service contract defining shopping cart operations for both authenticated users and guests.
 */
public interface CartService {

    /**
     * Retrieves an existing cart for the user or by token, or creates a new empty cart.
     *
     * @param user      The authenticated user (can be null for guest requests).
     * @param cartToken The anonymous session cart token (can be null).
     * @return CartResponse representing the current cart state.
     */
    CartResponse getOrCreateCart(User user, String cartToken);

    /**
     * Adds an item to the cart, verifying inventory availability.
     *
     * @param user      The authenticated user (can be null for guest requests).
     * @param cartToken The anonymous session cart token (can be null).
     * @param request   The product ID and requested quantity.
     * @return CartResponse representing the updated cart.
     */
    CartResponse addToCart(User user, String cartToken, AddToCartRequest request);

    /**
     * Updates an existing cart item's quantity.
     *
     * @param user      The authenticated user (can be null for guest requests).
     * @param cartToken The anonymous session cart token (can be null).
     * @param productId The ID of the product being updated.
     * @param request   The new quantity.
     * @return CartResponse representing the updated cart.
     */
    CartResponse updateCartItem(User user, String cartToken, Long productId, UpdateCartItemRequest request);

    /**
     * Removes an item completely from the cart.
     *
     * @param user      The authenticated user (can be null for guest requests).
     * @param cartToken The anonymous session cart token (can be null).
     * @param productId The ID of the product to remove.
     * @return CartResponse representing the updated cart.
     */
    CartResponse removeCartItem(User user, String cartToken, Long productId);

    /**
     * Clears all items from the specified cart.
     *
     * @param user      The authenticated user (can be null for guest requests).
     * @param cartToken The anonymous session cart token (can be null).
     */
    void clearCart(User user, String cartToken);
}

package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.UpdateCartItemRequest;
import com.ecommerce.api.dto.response.CartResponse;
import com.ecommerce.api.entity.User;

/**
 * Service for managing shopping carts for users and guests.
 */
public interface CartService {

    CartResponse getOrCreateCart(User user, String cartToken);

    CartResponse addToCart(User user, String cartToken, AddToCartRequest request);

    CartResponse updateCartItem(User user, String cartToken, Long productId, UpdateCartItemRequest request);

    CartResponse removeCartItem(User user, String cartToken, Long productId);

    void clearCart(User user, String cartToken);
}

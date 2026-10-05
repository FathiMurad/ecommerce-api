package com.ecommerce.api.dto.response;

import com.ecommerce.api.entity.Cart;
import com.ecommerce.api.entity.CartItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only projection representing complete cart details and financial totals.
 */
public record CartResponse(Long id, String cartToken, List<CartItemResponse> items, BigDecimal totalAmount,
                           int totalItems) {
    public static CartResponse fromEntity(Cart cart) {
        List<CartItemResponse> itemResponses = new ArrayList<>();
        int itemCount = 0;

        if (cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                itemResponses.add(CartItemResponse.fromEntity(item));
                if (item.getQuantity() != null) {
                    itemCount += item.getQuantity();
                }
            }
        }

        return new CartResponse(cart.getId(), cart.getCartToken(), itemResponses, cart.calculateTotalAmount(), itemCount);
    }
}

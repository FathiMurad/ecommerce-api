package com.ecommerce.api.dto.response;

import com.ecommerce.api.constant.MoneyConstants;
import com.ecommerce.api.entity.Cart;
import com.ecommerce.api.entity.CartItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Response payload representing complete shopping cart details, items, and financial totals.
 */
public record CartResponse(
        Long id,
        String cartToken,
        List<CartItemResponse> items,
        BigDecimal totalAmount,
        int totalItems
) {

    public static CartResponse empty() {
        return new CartResponse(
                null,
                null,
                List.of(),
                BigDecimal.ZERO.setScale(MoneyConstants.SCALE, RoundingMode.HALF_UP),
                0
        );
    }

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

        return new CartResponse(
                cart.getId(),
                cart.getCartToken(),
                itemResponses,
                cart.calculateTotalAmount(),
                itemCount
        );
    }
}

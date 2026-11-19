package com.ecommerce.api.dto.response;

import com.ecommerce.api.entity.CartItem;
import com.ecommerce.api.entity.ProductImage;

import java.math.BigDecimal;

/**
 * Response payload representing an individual product line item within a shopping cart.
 */
public record CartItemResponse(
        Long id,
        Long productId,
        String productName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal subtotal,
        String imageUrl
) {
    public static CartItemResponse fromEntity(CartItem item) {
        String primaryImageUrl = null;

        if (item.getProduct().getImages() != null) {
            for (ProductImage image : item.getProduct().getImages()) {
                if (image.getIsPrimary()) {
                    primaryImageUrl = image.getImageUrl();
                    break;
                }
            }

            if (primaryImageUrl == null && !item.getProduct().getImages().isEmpty()) {
                primaryImageUrl = item.getProduct().getImages().getFirst().getImageUrl();
            }
        }

        return new CartItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getProduct().getPrice(),
                item.getQuantity(),
                item.getSubtotal(),
                primaryImageUrl
        );
    }
}

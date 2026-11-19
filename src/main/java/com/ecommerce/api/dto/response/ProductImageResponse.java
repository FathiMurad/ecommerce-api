package com.ecommerce.api.dto.response;

import com.ecommerce.api.entity.ProductImage;

/**
 * Response payload representing a product image asset and its display metadata.
 */
public record ProductImageResponse(
        Long id,
        String imageUrl,
        Boolean isPrimary,
        Integer displayOrder
) {
    public static ProductImageResponse fromEntity(ProductImage image) {
        return new ProductImageResponse(
                image.getId(),
                image.getImageUrl(),
                image.getIsPrimary(),
                image.getDisplayOrder()
        );
    }
}

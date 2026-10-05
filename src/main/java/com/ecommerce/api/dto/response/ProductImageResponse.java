package com.ecommerce.api.dto.response;

import com.ecommerce.api.entity.ProductImage;

/**
 * Read-only projection representing an image asset in API responses.
 */
public record ProductImageResponse(Long id, String imageUrl, Boolean isPrimary, Integer displayOrder) {
    public static ProductImageResponse fromEntity(ProductImage image) {
        return new ProductImageResponse(image.getId(), image.getImageUrl(), image.getIsPrimary(), image.getDisplayOrder());
    }
}

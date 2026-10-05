package com.ecommerce.api.dto.response;

import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.ProductImage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only projection representing complete product details in API responses.
 */
public record ProductResponse(Long id, String name, String description, BigDecimal price, Integer stockQuantity,
                              Long categoryId, String categoryName, List<ProductImageResponse> images) {
    public static ProductResponse fromEntity(Product product) {
        List<ProductImageResponse> imageResponses = new ArrayList<>();

        if (product.getImages() != null) {
            for (ProductImage image : product.getImages()) {
                imageResponses.add(ProductImageResponse.fromEntity(image));
            }
        }

        Long categoryId = null;
        String categoryName = null;
        if (product.getCategory() != null) {
            categoryId = product.getCategory().getId();
            categoryName = product.getCategory().getName();
        }

        return new ProductResponse(product.getId(), product.getName(), product.getDescription(), product.getPrice(), product.getStockQuantity(), categoryId, categoryName, imageResponses);
    }
}

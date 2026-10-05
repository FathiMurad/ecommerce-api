package com.ecommerce.api.dto.response;

import com.ecommerce.api.entity.Category;

/**
 * Read-only projection representing category details in API responses.
 */
public record CategoryResponse(Long id, String name, String description) {
    public static CategoryResponse fromEntity(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription());
    }
}

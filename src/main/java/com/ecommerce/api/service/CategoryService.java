package com.ecommerce.api.service;

import com.ecommerce.api.dto.response.CategoryResponse;

import java.util.List;

/**
 * Service contract for category operations.
 */
public interface CategoryService {

    /**
     * Retrieves all available categories.
     *
     * @return List of CategoryResponse DTOs.
     */
    List<CategoryResponse> getAllCategories();

    /**
     * Retrieves a single category by its primary key.
     *
     * @param id The category identifier.
     * @return The CategoryResponse DTO.
     */
    CategoryResponse getCategoryById(Long id);
}
